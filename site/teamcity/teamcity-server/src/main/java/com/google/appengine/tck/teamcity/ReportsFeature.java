/*
 * Copyright 2013 Google Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.appengine.tck.teamcity;

import java.net.URI;

import jetbrains.buildServer.serverSide.BuildFeature;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.serverSide.BuildStatistics;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.serverSide.STest;
import jetbrains.buildServer.serverSide.STestRun;
import jetbrains.buildServer.tests.TestName;
import jetbrains.buildServer.util.EventDispatcher;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.BasicClientConnectionManager;
import org.apache.http.params.HttpParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Ales Justin
 */
public class ReportsFeature extends BuildFeature {
    private static final String TYPE = "appengine.tck.reports";
    private static final String URL = "http://192.168.30.235:8080";

    private HttpClient client;

    public ReportsFeature(EventDispatcher<BuildServerListener> dispatcher) {
        dispatcher.addListener(new BuildServerAdapter() {
            @Override
            public void buildFinished(SRunningBuild build) {
                handleBuildFinished(build);
            }
        });
    }

    public void start() {
        SchemeRegistry registry = new SchemeRegistry();
        registry.register(new Scheme("http", 8080, new PlainSocketFactory()));
        ClientConnectionManager ccm = new BasicClientConnectionManager(registry);
        client = new DefaultHttpClient(ccm);
    }

    public void stop() {
        if (client != null) {
            client.getConnectionManager().shutdown();
            client = null;
        }
    }

    @NotNull
    @Override
    public String getType() {
        return TYPE;
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return "Google App Engine TCK Reports";
    }

    @Nullable
    @Override
    public String getEditParametersUrl() {
        return null; // no params atm
    }

    protected void handleBuildFinished(SRunningBuild build) {
        SBuildType bt = build.getBuildType();
        if (bt == null) return;

        for (SBuildFeatureDescriptor feature : bt.getBuildFeatures()) {
            if (feature.getType().equals(TYPE) == false) continue;

            handleBuildFinished(build, feature);
        }
    }

    @SuppressWarnings("UnusedParameters")
    protected void handleBuildFinished(SRunningBuild build, SBuildFeatureDescriptor feature) {
        String buildType = build.getBuildTypeName();
        long buildId = build.getBuildId();

        BuildStatistics statistics = build.getFullStatistics();
        int failedTests = statistics.getFailedTestCount();
        int passedTests = statistics.getPassedTestCount();
        int ignoredTests = statistics.getIgnoredTestCount();

        try {
            HttpPut put = new HttpPut(new URI(URL));

            HttpParams params = put.getParams();
            params.setParameter("buildType", buildType);
            params.setParameter("buildId", buildId);
            params.setParameter("failedTests", failedTests);
            params.setParameter("passedTests", passedTests);
            params.setParameter("ignoredTests", ignoredTests);

            StringBuilder builder = new StringBuilder();
            for (STestRun tr : statistics.getFailedTests()) {
                STest test = tr.getTest();
                TestName name = test.getName();
                // com.acme.foo.SomeTest_#_testBar_#_this is err msg
                builder.append(name.getTestClass()).append("_#_").append(name.getTestMethodName()).append("_#_").append(tr.getFailureInfo().getStacktraceMessage()).append("\n");
            }
            put.setEntity(new StringEntity(builder.toString()));

            HttpResponse response = client.execute(put);
            System.out.println("Response: " + response);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}