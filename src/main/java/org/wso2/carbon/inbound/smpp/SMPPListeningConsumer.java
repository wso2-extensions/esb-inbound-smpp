/**
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 * <p>
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.inbound.smpp;

import com.google.common.util.concurrent.ListenableFuture;
import com.nurkiewicz.asyncretry.AsyncRetryExecutor;
import com.nurkiewicz.asyncretry.RetryContext;
import com.nurkiewicz.asyncretry.RetryExecutor;
import com.nurkiewicz.asyncretry.function.RetryRunnable;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.util.UUIDGenerator;
import org.apache.axis2.builder.Builder;
import org.apache.axis2.builder.BuilderUtil;
import org.apache.axis2.builder.SOAPBuilder;
import org.apache.axis2.transport.TransportUtils;
import org.apache.commons.io.input.AutoCloseInputStream;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.jsmpp.bean.AlertNotification;
import org.jsmpp.bean.BindType;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliverSm;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.extra.ProcessRequestException;
import org.jsmpp.extra.SessionState;
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.DataSmResult;
import org.jsmpp.session.MessageReceiverListener;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.Session;
import org.jsmpp.session.SessionStateListener;
import org.jsmpp.util.InvalidDeliveryReceiptException;
import org.wso2.carbon.inbound.endpoint.protocol.generic.GenericEventBasedConsumer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * SMPP inbound endpoint is used to listen and consume messages from SMSC via WSO2 ESB.
 *
 * @since 1.0.0.
 */
public class SMPPListeningConsumer extends GenericEventBasedConsumer {

    private static final Log logger = LogFactory.getLog(SMPPListeningConsumer.class);
    /**
     * IP address of the SMSC.
     */
    private String host;
    /**
     * Port to access the SMSC.
     */
    private int port;
    /**
     * Identifies the ESME system requesting to bind as a receiver with the SMSC.
     */
    private String systemId;
    /**
     * The password may be used by the SMSC to authenticate the ESME requesting to bind.
     */
    private String password;
    /**
     * Identifies the type of ESME system requesting to bind as a receiver with the SMSC.
     */
    private String systemType;
    /**
     * Indicates Type of Number of the ESME address.
     */
    private String addressTon;
    /**
     * Numbering Plan Indicator for ESME address.
     */
    private String addressNpi;
    /**
     * A single ESME address or a range of ESME addresses served via this SMPP receiver session.
     */
    private String addressRange;
    /**
     * SMPP Session used to communicate with SMSC.
     */
    private SMPPSession session;
    /**
     * An ESME bound as a Receiver or Transceiver is authorised to receive short messages from the SMSC.
     */
    private String bindType;
    /**
     * To check the connection to the SMSC.
     */
    private BindParameter bindParameter;
    /**
     * Used to check whether SMSC is connected or not.
     */
    private int enquireLinkTimer;
    /**
     * Time elapsed between smpp request and the corresponding response.
     */
    private int transactionTimer;
    /**
     * The Initial retry interval to reconnect with the SMSC.
     */
    private long reconnectInterval;
    /**
     * Start with Initial reconnectInterval delay until first retry attempt is made but if that one
     * fails, we should wait (reconnectInterval * exponentialFactor) times more.
     */
    private int exponentialFactor;
    /**
     * The Maximum no of retries, while connection with the SMSC is closed.
     */
    private int retryCount;
    /**
     * It increases the back off period for each retry attempt.
     * When the interval has reached the max interval, it is no longer increased.
     */
    private long maximumBackoffTime;
    /**
     * It can schedule commands to run after a given delay, or to execute periodically.
     */
    private ScheduledExecutorService scheduler;
    /**
     * An object that executes submitted Runnable tasks.
     */
    private RetryExecutor executor;
    /**
     *  The flag serves as a coordination mechanism to prevent race conditions during the shutdown process.
     */
    private volatile boolean isShuttingDown = false;

    public SMPPListeningConsumer(Properties smppProperties, String name,
                                 SynapseEnvironment synapseEnvironment, String injectingSeq,
                                 String onErrorSeq, boolean coordination, boolean sequential) {
        super(smppProperties, name, synapseEnvironment, injectingSeq, onErrorSeq, coordination,
                sequential);
        logger.info("Starting to load the SMPP Inbound Endpoint " + name);
        if (logger.isDebugEnabled()) {
            logger.debug("Starting to load the SMPP Properties for " + name);
        }
        this.host = properties.getProperty(SMPPConstants.HOST);
        if (StringUtils.isEmpty(host)) {
            throw new SynapseException("IP address of the SMSC (Host) is not set");
        }
        if (StringUtils.isEmpty(properties.getProperty(SMPPConstants.PORT))) {
            throw new SynapseException("Port to access the " + host + " is not set");
        } else {
            this.port = Integer.parseInt(properties.getProperty(SMPPConstants.PORT));
        }
        this.systemId = properties.getProperty(SMPPConstants.SYSTEM_ID);
        if (StringUtils.isEmpty(systemId)) {
            throw new SynapseException("System Id of the ESME is not set to connect with " + host);
        }
        this.password = properties.getProperty(SMPPConstants.PASSWORD);
        if (StringUtils.isEmpty(password)) {
            throw new SynapseException("Password of the ESME is not set to connect with " + host);
        }
        this.bindType = properties.getProperty(SMPPConstants.BIND_TYPE);
        if (StringUtils.isEmpty(bindType)) {
            throw new SynapseException("Bind Type of the ESME is not set to connect with " + host);
        }
        this.addressTon = properties.getProperty(SMPPConstants.ADDRESS_TON);
        if (StringUtils.isEmpty(addressTon)) {
            throw new SynapseException("Address TON value of the ESME is not set to connect with " + host);
        }
        this.addressNpi = properties.getProperty(SMPPConstants.ADDRESS_NPI);
        if (StringUtils.isEmpty(addressNpi)) {
            throw new SynapseException("Address NPI valueof the ESME is not set to connect with " + host);
        }
        this.systemType = properties.getProperty(SMPPConstants.SYSTEM_TYPE);
        if (StringUtils.isEmpty(systemType)) {
            this.systemType = SMPPConstants.NULL;
        }
        this.addressRange = properties.getProperty(SMPPConstants.ADDRESS_RANGE);
        if (StringUtils.isEmpty(addressRange)) {
            this.addressRange = SMPPConstants.NULL;
        }
        String enquireLinktimer = properties.getProperty(SMPPConstants.ENQUIRE_LINK_TIMER);
        if (StringUtils.isEmpty(enquireLinktimer)) {
            enquireLinktimer = SMPPConstants.ENQUIRELINK_TIMER_DEFAULT;
        }
        enquireLinkTimer = Integer.parseInt(enquireLinktimer);
        String transactiontimer = properties.getProperty(SMPPConstants.TRANSACTION_TIMER);
        if (StringUtils.isEmpty(transactiontimer)) {
            transactiontimer = SMPPConstants.TRANSACTION_TIMER_DEFAULT;
        }
        transactionTimer = Integer.parseInt(transactiontimer);
        String reconnectinterval = properties.getProperty(SMPPConstants.RECONNECT_INTERVAL);
        if (StringUtils.isEmpty(reconnectinterval)) {
            reconnectinterval = SMPPConstants.RECONNECT_INTERVAL_DEFAULT;
        }
        this.reconnectInterval = Long.parseLong(reconnectinterval);
        String retrycount = properties.getProperty(SMPPConstants.RETRY_COUNT);
        if (StringUtils.isEmpty(retrycount)) {
            retrycount = SMPPConstants.RETRY_COUNT_DEFAULT;
        }
        this.retryCount = Integer.parseInt(retrycount);
        if (retryCount < 0) {
            retryCount = Integer.MAX_VALUE;
        }
        String exponentialfactor = properties.getProperty(SMPPConstants.EXPONENTIAL_FACTOR);
        if (StringUtils.isEmpty(exponentialfactor)) {
            exponentialfactor = SMPPConstants.EXPONENTIAL_FACTOR_DEFAULT;
        }
        this.exponentialFactor = Integer.parseInt(exponentialfactor);
        String maximumBackofftime = properties.getProperty(SMPPConstants.MAXIMUM_BACK_OFF_TIME);
        if (StringUtils.isEmpty(maximumBackofftime)) {
            maximumBackofftime = SMPPConstants.MAXIMUM_BACK_OFF_TIME_DEFAULT;
        }
        this.maximumBackoffTime = Integer.parseInt(maximumBackofftime);
        if (logger.isDebugEnabled()) {
            logger.debug("Loaded the SMPP Parameters with Host : " + host
                    + " , Port : " + port + " , SystemId : " + systemId
                    + " , Password : " + password + " , SystemType : "
                    + systemType + " , AddressTon : " + addressTon
                    + " , BindType : " + bindType
                    + " , AddressNpi : " + addressNpi + ", AddressRange : "
                    + addressRange + ", EnquireLinkTimer : " + enquireLinkTimer
                    + ", TransactionTimer : " + transactionTimer
                    + ", ReconnectInterval : " + reconnectInterval
                    + ", RetryCount : " + retryCount
                    + ", ExponentialFactor : " + exponentialFactor
                    + ", MaximumBackoffTime : " + maximumBackoffTime + "for " + name);
        }
        logger.info("Initialized the SMPP inbound consumer " + name);
    }

    /**
     * Create connection with SMSC and listen to retrieve the messages. Then inject
     * according to the registered handler.
     */
    public void listen() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        executor = new AsyncRetryExecutor(scheduler).retryOn(ConnectException.class).
                //(reconnectInterval) ms times exponentialFactor after each retry.
                        withExponentialBackoff(reconnectInterval, exponentialFactor).
                // Maximum backoff time.
                        withMaxDelay(maximumBackoffTime).
                        withUniformJitter().
                        withMaxRetries(retryCount);
        if (logger.isDebugEnabled()) {
            logger.debug("Started to Listen SMPP messages for " + name);
        }
        try {
            session = getSession();
        } catch (IOException e) {
            reconnect();
            throw new SynapseException("Error while getting the SMPP session", e);
        }
    }

    /**
     * Get the session. If the session still null or not in bound state, then IO
     * exception will be thrown.
     *
     * @return the valid session.
     * @throws IOException if there is no valid session or session creation is
     *                     invalid.
     */
    private SMPPSession getSession() throws IOException {
        if (session == null) {
            logger.info("Initiate session for the first time to " + host + ":" + port + " for " + name);
            session = newSession();
        } else if (!session.getSessionState().isBound()) {
            throw new SynapseException("There is no valid session yet");
        }
        return session;
    }

    /**
     * Create new SMPPSession.
     *
     * @return the {@link SMPPSession}.
     * @throws IOException if the creation of new session failed.
     */
    private SMPPSession newSession() throws IOException {
        logger.info("Trying to create new session " + name);
        bindParameter = new BindParameter(BindType.valueOf(bindType), systemId, password, systemType,
                TypeOfNumber.valueOf(addressTon), NumberingPlanIndicator.valueOf(addressNpi), addressRange);
        SMPPSession session = new SMPPSession(host, port, bindParameter);
        logger.info("Session created successfully");
        session.setEnquireLinkTimer(enquireLinkTimer);
        session.setTransactionTimer(transactionTimer);
        // Set listener to receive notification if there is any session state changes.
        session.addSessionStateListener(new SessionStateListenerImpl());
        if (logger.isDebugEnabled()) {
            logger.debug("Listening SMPP messages for " + name);
        }
        // Set listener to receive SMPP messages.
        session.setMessageReceiverListener(new MessageReceiverListenerImpl());
        return session;
    }

    /**
     * Reconnect session after specified interval.
     */
    private void reconnect() {
        if (isShuttingDown || scheduler == null || scheduler.isShutdown()) {
            return;
        }

        try {
            final ListenableFuture future = executor.doWithRetry(new RetryRunnable() {
                @Override
                public void run(RetryContext retryContext) throws Exception {
                    if (isShuttingDown) {
                        throw new InterruptedException("Shutdown requested");
                    }
                    newSession();
                }
            });
        } catch (Exception e) {
            logger.error("Failed to schedule reconnection for " + name, e);
        }
    }

    /**
     * Close the connection with the SMSC and stop all the threads.
     */
    public void destroy() {
        isShuttingDown = true;

        if (session != null) {
            session.unbindAndClose();
            if (logger.isDebugEnabled()) {
                logger.debug("The SMPP connection has been shutdown ! for " + name);
            }
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            if (logger.isDebugEnabled()) {
                logger.debug("The Scheduler has been shutdown ! for " + name);
            }
        }
    }

    public void resume() {
        isShuttingDown = false;

        try {
            session = newSession();
        } catch (IOException e) {
            reconnect();
            throw new SynapseException("Error while getting the SMPP session", e);
        }
    }

    /**
     * This class will receive the notification from {@link SMPPSession} for the
     * state changes. It will schedule to re-initialize session.
     */
    private class SessionStateListenerImpl implements SessionStateListener {
        @Override
        public void onStateChange(SessionState sessionState, SessionState sessionState1, Object o) {
            if (isShuttingDown) {
                return;
            }

            if (sessionState.equals(SessionState.CLOSED)) {
                logger.info("Session closed for " + name);
                reconnect();
            }
        }
    }

    /**
     * This class will receive the notification when the SMPP message is received.
     * Then get the messages and inject into the sequence.
     */
    private class MessageReceiverListenerImpl implements MessageReceiverListener {
        public void onAcceptDeliverSm(DeliverSm deliverSm) throws ProcessRequestException {
            // inject short message into the sequence.
            MessageContext msgCtx;
            msgCtx = createMessageContext();
            msgCtx.setProperty("SMPP_MessageId", deliverSm.getSmDefaultMsgId());
            msgCtx.setProperty("SMPP_SourceAddress", deliverSm.getSourceAddr());
            msgCtx.setProperty("SMPP_DestiationAddress", deliverSm.getDestAddress());
            msgCtx.setProperty("SMPP_DataCoding", deliverSm.getDataCoding());
            msgCtx.setProperty("SMPP_DestinationAddressNPI", deliverSm.getDestAddrNpi());
            msgCtx.setProperty("SMPP_DestinationAddressTON", deliverSm.getDestAddrTon());
            msgCtx.setProperty("SMPP_ESMClass", deliverSm.getEsmClass());
            msgCtx.setProperty("SMPP_PriorityFlag", deliverSm.getPriorityFlag());
            msgCtx.setProperty("SMPP_ProtocolId", deliverSm.getProtocolId());
            msgCtx.setProperty("SMPP_RegisteredDelivery", deliverSm.getRegisteredDelivery());
            msgCtx.setProperty("SMPP_ReplaceIfPresentFlag", deliverSm.getReplaceIfPresent());
            msgCtx.setProperty("SMPP_ScheduleDeliveryTime", deliverSm.getScheduleDeliveryTime());
            msgCtx.setProperty("SMPP_SequenceNumber", deliverSm.getSequenceNumber());
            msgCtx.setProperty("SMPP_ServiceType", deliverSm.getServiceType());
            msgCtx.setProperty("SMPP_SourceAddressNPI", deliverSm.getSourceAddrNpi());
            msgCtx.setProperty("SMPP_SourceAddressTON", deliverSm.getSourceAddrTon());
            msgCtx.setProperty("SMPP_ValidityPeriod", deliverSm.getValidityPeriod());

            try {
                msgCtx.setProperty("ShortMessageAsDeliveryReceipt", deliverSm.getShortMessageAsDeliveryReceipt());
            } catch (InvalidDeliveryReceiptException e) {
                logger.error("InvalidDeliveryReceipt");
            }
            injectMessage(new String(deliverSm.getShortMessage()), SMPPConstants.CONTENT_TYPE, msgCtx);
        }

        public void onAcceptAlertNotification(AlertNotification alertNotification) {
            if (logger.isDebugEnabled()) {
                logger.debug("onAcceptAlertNotification");
            }
        }

        public DataSmResult onAcceptDataSm(DataSm dataSm, Session source) throws ProcessRequestException {
            if (logger.isDebugEnabled()) {
                logger.debug("onAcceptDataSm");
            }
            return null;
        }
    }

    /**
     * Inject the message into the sequence.
     */
    private boolean injectMessage(String strMessage, String contentType, MessageContext msgCtx) {
        AutoCloseInputStream in = new AutoCloseInputStream(new ByteArrayInputStream(strMessage.getBytes()));

        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Processed Custom inbound EP Message of Content-type : " + contentType + " for " + name);
            }

            org.apache.axis2.context.MessageContext axis2MsgCtx = ((Axis2MessageContext) msgCtx).getAxis2MessageContext();
            Object builder;
            if (StringUtils.isEmpty(contentType)) {
                logger.debug("No content type specified. Using SOAP builder for " + name);
                builder = new SOAPBuilder();
            } else {
                int index = contentType.indexOf(59);
                String type = index > 0 ? contentType.substring(0, index) : contentType;
                builder = BuilderUtil.getBuilderFromSelector(type, axis2MsgCtx);
                if (builder == null) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("No message builder found for type \'" + type + "\'. Falling back to SOAP." + name);
                    }

                    builder = new SOAPBuilder();
                }
            }

            OMElement documentElement = ((Builder) builder).processDocument(in, contentType, axis2MsgCtx);
            msgCtx.setEnvelope(TransportUtils.createSOAPEnvelope(documentElement));
            if (this.injectingSeq == null || "".equals(this.injectingSeq)) {
                logger.error("Sequence name not specified. Sequence : " + this.injectingSeq);
                return false;
            }

            SequenceMediator seq = (SequenceMediator) this.synapseEnvironment.getSynapseConfiguration().getSequence(this.injectingSeq);
            if (seq != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("injecting message to sequence : " + this.injectingSeq);
                }

                seq.setErrorHandler(this.onErrorSeq);
                if (!seq.isInitialized()) {
                    seq.init(this.synapseEnvironment);
                }

                if (!this.synapseEnvironment.injectInbound(msgCtx, seq, this.sequential)) {
                    return false;
                }
            } else {
                logger.error("Sequence: " + this.injectingSeq + " not found" + name);
            }
        } catch (Exception e) {
            throw new SynapseException("Error while processing the Amazon SQS Message ", e);
        }

        return true;
    }

    /**
     * Create the message context.
     */
    private MessageContext createMessageContext() {
        MessageContext msgCtx = this.synapseEnvironment.createMessageContext();
        org.apache.axis2.context.MessageContext axis2MsgCtx = ((Axis2MessageContext) msgCtx).getAxis2MessageContext();
        axis2MsgCtx.setServerSide(true);
        axis2MsgCtx.setMessageID(UUIDGenerator.getUUID());
        return msgCtx;
    }
}