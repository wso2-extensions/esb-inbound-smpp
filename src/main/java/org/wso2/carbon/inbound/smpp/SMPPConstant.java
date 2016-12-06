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
public class SMPPConstant {
    public static final String HOST = "host";
    public static final String PORT = "port";
    public static final String SYSTEMID = "systemId";
    public static final String PASSWORD = "password";
    public static final String SYSTEMTYPE = "systemType";
    public static final String ADDRESSTON = "addressTon";
    public static final String ADDRESSNPI = "addressNpi";
    public static final String NULL = "null";
    public static final String BINDTYPE = "bindType";
    public static final String ADDRESSRANGE ="addressRange";
    public static final String CONTENT_TYPE = "text/plain";
    public static final String ENQUIRELINK_TIMER = "enquireLinkTimer";
    public static final String ENQUIRELINK_TIMER_DEFAULT = "10000";
    public static final String TRANSACTION_TIMER = "transactionTimer";
    public static final String TRANSACTION_TIMER_DEFAULT = "200";
    public static final String RECONNECT_INTERVAL = "reconnectInterval";
    public static final String RECONNECT_INTERVAL_DEFAULT = "5000";
    public static final String RETRY_COUNT = "retryCount";
    public static final String RETRY_COUNT_DEFAULT = "5";
}