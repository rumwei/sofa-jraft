/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alipay.sofa.jraft.error;

/**
 * Log not found exception, the log may be deleted.
 *
 * @author boyan (boyan@alibaba-inc.com)
 * <p>
 * 2018-Apr-23 3:07:56 PM
 */
public class LogNotFoundException extends IllegalStateException {

    private static final long serialVersionUID = -140969527148366390L;

    public LogNotFoundException() {
        super();
    }

    public LogNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public LogNotFoundException(String s) {
        super(s);
    }

    public LogNotFoundException(Throwable cause) {
        super(cause);
    }
}
