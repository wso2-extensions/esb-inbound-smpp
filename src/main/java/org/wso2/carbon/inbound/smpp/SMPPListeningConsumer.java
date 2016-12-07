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

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseException;
import org.apache.synapse.core.SynapseEnvironment;
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
import org.wso2.carbon.inbound.endpoint.protocol.generic.GenericEventBasedConsumer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

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
     * The retry interval to reconnect with the SMSC.
     */
    private long reconnectInterval;
    /**
     * The retry count while connection with the SMSC is closed.
     */
    private double retryCount;
    /**
     * Keep all the thread references that are created when the reconnection to SMSC is happened.
     */
    private ArrayList<Thread> threadList = new ArrayList<Thread>();

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
        if (logger.isDebugEnabled()) {
            logger.debug("Loaded the SMPP Parameters with Host : " + host
                    + " , Port : " + port + " , SystemId : " + systemId
                    + " , Password : " + password + " , SystemType : "
                    + systemType + " , AddressTon : " + addressTon
                    + " , AddressNpi : " + addressNpi + ", AddressRange : "
                    + addressRange + ", EnquireLinkTimer : " + enquireLinkTimer
                    + ", TransactionTimer : " + transactionTimer
                    + ", ReconnectInterval : " + reconnectInterval
                    + ", RetryCount : " + retryCount + "for " + name);
        }
        logger.info("Initialized the SMPP inbound consumer " + name);
    }

    /**
     * Create connection with SMSC and listen to retrieve the messages. Then inject
     * according to the registered handler.
     */
    public void listen() {
        if (logger.isDebugEnabled()) {
            logger.debug("Started to Listen SMPP messages for " + name);
        }
        try {
            session = getSession();
        } catch (IOException e) {
            reconnectAfter(reconnectInterval);
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
        logger.info("Creating new session " + name);
        bindParameter = new BindParameter(BindType.valueOf(bindType), systemId, password, systemType,
                TypeOfNumber.valueOf(addressTon), NumberingPlanIndicator.valueOf(addressNpi), addressRange);
        SMPPSession session = new SMPPSession(host, port, bindParameter);
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
     *
     * @param timeInMillis is the interval.
     */
    private void reconnectAfter(final long timeInMillis) {
        new Thread() {
            @Override
            public void run() {
                logger.info("Schedule reconnect after " + timeInMillis + " millis for " + name);
                try {
                    Thread.sleep(timeInMillis);
                } catch (InterruptedException e) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Unable to sleep the thread after " + timeInMillis + " millis for " + name);
                    }
                }
                int attempt = 0;
                if (retryCount < 0) {
                    retryCount = Double.POSITIVE_INFINITY;
                }
                while ((session == null || session.getSessionState().equals(SessionState.CLOSED)) && attempt < retryCount) {
                    try {
                        attempt = ++attempt;
                        logger.info("Reconnecting attempt #" + (attempt) + "... for " + name);
                        session = newSession();
                    } catch (IOException e) {
                        logger.error("Failed opening connection and bind to " + host + ":"
                                + port + " for " + name, e);
                        // wait for a second
                        try {
                            Thread.sleep(timeInMillis);
                        } catch (InterruptedException ee) {
                            if (logger.isDebugEnabled()) {
                                logger.debug("Unable to sleep the thread after " + timeInMillis + " millis for " + name);
                            }
                        }
                    }
                }
                if (session == null || session.getSessionState().equals(SessionState.CLOSED)) {
                    throw new SynapseException(name + " Could not connect to SMSC. Error while creating connection");
                }
            }
        }.start();
        threadList.add(Thread.currentThread());
    }

    /**
     * Close the connection with the SMSC and stop all the threads.
     */
    public void destroy() {
        if (session != null) {
            session.unbindAndClose();
            if (logger.isDebugEnabled()) {
                logger.debug("The SMPP connection has been shutdown ! for " + name);
            }
        }
        for (Thread thread : threadList) {
            if (!thread.isInterrupted()) {
                thread.interrupt();
                if (logger.isDebugEnabled()) {
                    logger.debug("Thread " + thread.getName() + " successfully stopped for " + name);
                }
            }
        }
    }

    /**
     * This class will receive the notification from {@link SMPPSession} for the
     * state changes. It will schedule to re-initialize session.
     */
    private class SessionStateListenerImpl implements SessionStateListener {
        @Override
        public void onStateChange(SessionState sessionState, SessionState sessionState1, Object o) {
            if (sessionState.equals(SessionState.CLOSED)) {
                logger.info("Session closed for " + name);
                reconnectAfter(reconnectInterval);
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
            injectMessage(new String(deliverSm.getShortMessage()), SMPPConstants.CONTENT_TYPE);
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
}