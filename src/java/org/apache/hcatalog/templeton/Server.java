/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hcatalog.templeton;

import java.io.IOException;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.apache.commons.exec.ExecuteException;

/**
 * The Templeton Web API server.
 */
@Path("/v1")
public class Server {
    public static final String STATUS_MSG
        = "{\"status\": \"ok\", \"version\": \"v1\"}\n";
    public static final String USER_PARAM = "user.name";

    private static ExecService execService = ExecService.getInstance();
    private static AppConfig appConf = AppConfig.getInstance();

    /**
     * Check the status of this server.
     */
    @GET
    @Path("status.json")
    @Produces({MediaType.APPLICATION_JSON})
    public String status() {
        return STATUS_MSG;
    }

    /**
     * Exececute an hcat ddl expression on the local box.  It is run
     * as the authenticated user and rate limited.
     */
    @POST
    @Path("ddl.json")
    @Produces({MediaType.APPLICATION_JSON})
    public ExecBean ddl(@FormParam(USER_PARAM) String user,
                        @FormParam("exec") String exec,
                        @FormParam("group") String group,
                        @FormParam("permissions") String permissions)
        throws NotAuthorizedException, BusyException, BadParam,
               ExecuteException, IOException
    {
        verifyUser(user);
        verifyParam(exec, "exec");

        HcatDelegator d = new HcatDelegator(appConf, execService);
        return d.run(user, exec, group, permissions);
    }

    /**
     * Run a MapReduce Streaming job.
     */
    @POST
    @Path("mapreduce/streaming.json")
    @Produces({MediaType.APPLICATION_JSON})
    public EnqueueBean mapReduceStreaming(@FormParam(USER_PARAM) String user,
                                          @FormParam("input") List<String> inputs,
                                          @FormParam("output") String output,
                                          @FormParam("mapper") String mapper,
                                          @FormParam("reducer") String reducer,
                                          @FormParam("file") List<String> files,
                                          @FormParam("define") List<String> defines,
                                          @FormParam("cmdenv") List<String> cmdenv)
        throws NotAuthorizedException, BusyException, BadParam, QueueException,
               ExecuteException, IOException
    {
        verifyUser(user);
        verifyParam(inputs, "input");
        verifyParam(mapper, "mapper");
        verifyParam(reducer, "reducer");

        StreamingDelegator d = new StreamingDelegator(appConf, execService);
        return d.run(user, inputs, output, mapper, reducer);
    }

    /**
     * Run a MapReduce Jar job.
     */
    @POST
    @Path("mapreduce/jar.json")
    @Produces({MediaType.APPLICATION_JSON})
    public EnqueueBean mapReduceJar(@FormParam(USER_PARAM) String user,
                                    @FormParam("jar") String jar,
                                    @FormParam("class") String mainClass,
                                    @FormParam("libjars") String libjars,
                                    @FormParam("files") String files,
                                    @FormParam("arg") List<String> args,
                                    @FormParam("define") List<String> defines,
                                    @FormParam("statusdir") String statusdir)
        throws NotAuthorizedException, BusyException, BadParam, QueueException,
        ExecuteException, IOException
    {
        verifyUser(user);
        verifyParam(jar, "jar");
        verifyParam(mainClass, "class");

        JarDelegator d = new JarDelegator(appConf, execService);
        return d.run(user,
                     jar, mainClass,
                     libjars, files, args,
                     defines, statusdir);
    }

    /**
     * Run a Pig job.
     */
    @POST
    @Path("pig.json")
    @Produces({MediaType.APPLICATION_JSON})
    public EnqueueBean pig(@FormParam(USER_PARAM) String user,
                           @FormParam("execute") String execute,
                           @FormParam("file") String srcFile,
                           @FormParam("arg") List<String> pigArgs,
                           @FormParam("files") String otherFiles,
                           @FormParam("statusdir") String statusdir)
        throws NotAuthorizedException, BusyException, BadParam, QueueException,
        ExecuteException, IOException
    {
        verifyUser(user);
        if (execute == null && srcFile == null)
            throw new BadParam("Either execute or file parameter required");

        PigDelegator d = new PigDelegator(appConf, execService);
        return d.run(user,
                     execute, srcFile,
                     pigArgs, otherFiles,
                     statusdir);
    }

    /**
     * Run a Hive job.
     */
    @POST
    @Path("hive.json")
    @Produces({MediaType.APPLICATION_JSON})
    public EnqueueBean pig(@FormParam(USER_PARAM) String user,
                           @FormParam("execute") String execute,
                           @FormParam("file") String srcFile,
                           @FormParam("statusdir") String statusdir)
        throws NotAuthorizedException, BusyException, BadParam, QueueException,
        ExecuteException, IOException
    {
        verifyUser(user);
        if (execute == null && srcFile == null)
            throw new BadParam("Either execute or file parameter required");

        HiveDelegator d = new HiveDelegator(appConf, execService);
        return d.run(user, execute, srcFile, statusdir);
    }

    /**
     * Return the status of the jobid.
     */
    @GET
    @Path("queue/{jobid}.json")
    @Produces({MediaType.APPLICATION_JSON})
    public QueueStatusBean showQueueId(@QueryParam(USER_PARAM) String user,
                                       @PathParam("jobid") String jobid)
        throws NotAuthorizedException, BadParam, IOException
    {
        verifyUser(user);
        verifyParam(jobid, ":jobid");

        StatusDelegator d = new StatusDelegator(appConf, execService);
        return d.run(user, jobid);
    }

    /**
     * Kill a job in the queue.
     */
    @DELETE
    @Path("queue/{jobid}.json")
    @Produces({MediaType.APPLICATION_JSON})
    public QueueStatusBean deleteQueueId(@QueryParam(USER_PARAM) String user,
                                       @PathParam("jobid") String jobid)
        throws NotAuthorizedException, BadParam, IOException
    {
        verifyUser(user);
        verifyParam(jobid, ":jobid");

        DeleteDelegator d = new DeleteDelegator(appConf, execService);
        return d.run(user, jobid);
    }

    /**
     * Verify that we have a valid user.  Throw an exception if invalid.
     */
    public void verifyUser(String user)
        throws NotAuthorizedException
    {
        if (user == null)
            throw new NotAuthorizedException("missing " + USER_PARAM + " parameter");
    }

    /**
     * Verify that the parameter exists.  Throw an exception if invalid.
     */
    public void verifyParam(String param, String name)
        throws BadParam
    {
        if (param == null)
            throw new BadParam("Missing " + name + " parameter");
    }

    /**
     * Verify that the parameter exists.  Throw an exception if invalid.
     */
    public void verifyParam(List<String> param, String name)
        throws BadParam
    {
        if (param == null || param.isEmpty())
            throw new BadParam("Missing " + name + " parameter");
    }
}
