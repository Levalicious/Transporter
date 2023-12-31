/*
 * Copyright 2012 frdfsnlght <frdfsnlght@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bennedum.transporter.api;

/**
 * Represents an exception about a gate.
 * 
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class GateException extends TransporterException {
    
    /**
     * Creates a new exception.
     * 
     * @param msg   a format string
     * @param args  zero or more optional arguments used by the format string
     */
    public GateException(String msg, Object ... args) {
        super(String.format(msg, args));
    }
    
}
