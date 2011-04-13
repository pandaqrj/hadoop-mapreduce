/**
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

package org.apache.hadoop.mapreduce.v2.hs;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.v2.api.records.JobId;
import org.apache.hadoop.mapreduce.TypeConverter;
import org.apache.hadoop.mapreduce.v2.app.job.Job;
import org.apache.hadoop.mapreduce.v2.hs.CompletedJob;
import org.apache.hadoop.yarn.YarnException;
import org.apache.hadoop.yarn.api.records.ApplicationId;

/*
 * Loads and manages the Job history cache.
 */
public class JobHistory implements HistoryContext {

  private Map<JobId, Job> completedJobCache =
    new ConcurrentHashMap<JobId, Job>();
  private Configuration conf;
  private final LinkedList<Job> jobQ = new LinkedList<Job>();
  private static final Log LOG = LogFactory.getLog(JobHistory.class);
  private final int retiredJobsCacheSize = 1000; //TODO make it configurable


  public JobHistory(Configuration conf) {
    this.conf = conf;
  }
  @Override
  public synchronized Job getJob(JobId jobId) {
    Job job = completedJobCache.get(jobId);
    if (job == null) {
      try {
        job = new CompletedJob(conf, jobId);
      } catch (IOException e) {
        LOG.warn("HistoryContext getJob failed " + e);
        throw new YarnException(e);
      }
      completedJobCache.put(jobId, job);
      jobQ.add(job);
      if (jobQ.size() > retiredJobsCacheSize) {
         Job removed = jobQ.remove();
         completedJobCache.remove(removed.getID());
      }
    }
    return job;
  }

  @Override
  public Map<JobId, Job> getAllJobs(ApplicationId appID) {
    //currently there is 1 to 1 mapping between app and job id
    org.apache.hadoop.mapreduce.JobID oldJobID = TypeConverter.fromYarn(appID);
    Map<JobId, Job> jobs = new HashMap<JobId, Job>();
    JobId jobID = TypeConverter.toYarn(oldJobID);
    jobs.put(jobID, getJob(jobID));
    return jobs;
  }
}