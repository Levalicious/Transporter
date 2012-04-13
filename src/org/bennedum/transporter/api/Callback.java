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
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public abstract class Callback<T> {
    
    private long requestId;
    private long requestTime;
    
    public Callback() {
        requestTime = System.currentTimeMillis();
    }
    
    public long getRequestId() {
        return requestId;
    }
    
    public void setRequestId(long rid) {
        requestId = rid;
    }
    
    public long getRequestTime() {
        return requestTime;
    }
     
    public long getAge() {
        return System.currentTimeMillis() - requestTime;
    }
    
    abstract public void onSuccess(T t);
    
    public void onFailure(RemoteException e) {}
    
}
