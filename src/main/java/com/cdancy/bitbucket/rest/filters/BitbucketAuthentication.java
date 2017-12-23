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

package com.cdancy.bitbucket.rest.filters;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.io.BaseEncoding.base64;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.jclouds.domain.Credentials;
import org.jclouds.http.HttpException;
import org.jclouds.http.HttpRequest;
import org.jclouds.http.HttpRequestFilter;
import org.jclouds.location.Provider;

import com.google.common.base.Supplier;
import com.google.common.net.HttpHeaders;

@Singleton
public class BitbucketAuthentication implements HttpRequestFilter {
    private final Supplier<Credentials> creds;

    public static final String BASE64_REGEX = "^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{4}|[A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)$";

    @Inject
    BitbucketAuthentication(@Provider final Supplier<Credentials> creds) {
        this.creds = creds;
    }

    @Override
    public HttpRequest filter(final HttpRequest request) throws HttpException {
        final Credentials currentCreds = checkNotNull(creds.get(), "credential supplier returned null");
        if (currentCreds.credential != null && currentCreds.credential.trim().length() > 0) {
            /*
            * client can pass in credential string in 1 of 5 ways:
            *
            * 1.) As colon delimited username and password:
            *     a.) admin:password
            *     b.) basic@admin:password
            *
            * 2.) As base64 encoded value of colon delimited username and
            * password:
            *     a.) YWRtaW46cGFzc3dvcmQ=
            *     b.) basic@YWRtaW46cGFzc3dvcmQ=
            *
            * 3.) As Bitbucket personal access token obtained from Bitbucket:
            *     a.) bearer@9DfK3AF9Jeke1O0dkKX5kDswps43FEDlf5Frkspma21M
            */
            String foundCredential = currentCreds.credential;

            if (foundCredential.startsWith("bearer@")) {
                final String[] credArray = foundCredential.split("@",2);
                return request.toBuilder().addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + credArray[1]).build();
            }

            if (foundCredential.startsWith("basic@")) {
                foundCredential = foundCredential.split("@",2)[1];
            }

            if (foundCredential.contains(":")) {
                foundCredential = base64().encode(foundCredential.getBytes());
            }

            if (isBase64Encoded(foundCredential)) {
                return request.toBuilder().addHeader(HttpHeaders.AUTHORIZATION, "Basic " + foundCredential).build();
            } else {
                throw new IllegalArgumentException("Credential is not Basic, Bearer or base64 format: credential=" + foundCredential);
            }

        } else {
            return request.toBuilder().build();
        }
    }

    private boolean isBase64Encoded(final String possiblyEncodedString) {
        return possiblyEncodedString.matches(BASE64_REGEX);
    }
}
