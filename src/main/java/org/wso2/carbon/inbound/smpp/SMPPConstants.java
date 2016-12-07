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

/**
 * Common constants used by SMPP inbound endpoint.
 *
 * @since 1.0.0.
 */
public class SMPPConstants {
    public static final String HOST = "host";
    public static final String PORT = "port";
    public static final String SYSTEM_ID = "systemId";
    public static final String PASSWORD = "password";
    public static final String SYSTEM_TYPE = "systemType";
    public static final String ADDRESS_TON = "addressTon";
    public static final String ADDRESS_NPI = "addressNpi";
    public static final String NULL = "null";
    public static final String BIND_TYPE = "bindType";
    public static final String ADDRESS_RANGE ="addressRange";
    public static final String CONTENT_TYPE = "text/plain";
    public static final String ENQUIRE_LINK_TIMER = "enquireLinkTimer";
    public static final String ENQUIRELINK_TIMER_DEFAULT = "10000";
    public static final String TRANSACTION_TIMER = "transactionTimer";
    public static final String TRANSACTION_TIMER_DEFAULT = "200";
    public static final String RECONNECT_INTERVAL = "reconnectInterval";
    public static final String RECONNECT_INTERVAL_DEFAULT = "5000";
    public static final String RETRY_COUNT = "retryCount";
    public static final String RETRY_COUNT_DEFAULT = "5";
}