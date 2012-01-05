/*
 * Copyright 2009 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.hornetq.jms.client;

import java.io.Serializable;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.QueueConnection;
import javax.jms.TopicConnection;
import javax.jms.XAConnection;
import javax.jms.XAQueueConnection;
import javax.jms.XATopicConnection;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.Referenceable;

import org.hornetq.api.core.DiscoveryGroupConfiguration;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.api.jms.JMSFactoryType;
import org.hornetq.jms.referenceable.ConnectionFactoryObjectFactory;
import org.hornetq.jms.referenceable.SerializableObjectRefAddr;

/**
 * HornetQ implementation of a JMS ConnectionFactory.
 *
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision$</tt> $Id$
 */
public class HornetQConnectionFactory implements Serializable, Referenceable
{
   private final static long serialVersionUID = -2810634789345348326L;

   private final ServerLocator serverLocator;

   private String clientID;

   private int dupsOKBatchSize = HornetQClient.DEFAULT_ACK_BATCH_SIZE;

   private int transactionBatchSize = HornetQClient.DEFAULT_ACK_BATCH_SIZE;

   private boolean readOnly;

   public HornetQConnectionFactory()
   {
      serverLocator = null;
   }

   public HornetQConnectionFactory(final ServerLocator serverLocator)
   {
      this.serverLocator = serverLocator;

      serverLocator.disableFinalizeCheck();
   }

   public HornetQConnectionFactory(final boolean ha, final DiscoveryGroupConfiguration groupConfiguration)
   {
      if (ha)
      {
         serverLocator = HornetQClient.createServerLocatorWithHA(groupConfiguration);
      }
      else
      {
         serverLocator = HornetQClient.createServerLocatorWithoutHA(groupConfiguration);
      }

      serverLocator.disableFinalizeCheck();
   }

   public HornetQConnectionFactory(final boolean ha, final TransportConfiguration... initialConnectors)
   {
      if (ha)
      {
         serverLocator = HornetQClient.createServerLocatorWithHA(initialConnectors);
      }
      else
      {
         serverLocator = HornetQClient.createServerLocatorWithoutHA(initialConnectors);
      }

      serverLocator.disableFinalizeCheck();
   }

   // ConnectionFactory implementation -------------------------------------------------------------

   public Connection createConnection() throws JMSException
   {
      return createConnection(null, null);
   }

   public Connection createConnection(final String username, final String password) throws JMSException
   {
      return createConnectionInternal(username, password, false, HornetQConnection.TYPE_GENERIC_CONNECTION);
   }

   // QueueConnectionFactory implementation --------------------------------------------------------

   public QueueConnection createQueueConnection() throws JMSException
   {
      return createQueueConnection(null, null);
   }

   public QueueConnection createQueueConnection(final String username, final String password) throws JMSException
   {
      return createConnectionInternal(username, password, false, HornetQConnection.TYPE_QUEUE_CONNECTION);
   }

   // TopicConnectionFactory implementation --------------------------------------------------------

   public TopicConnection createTopicConnection() throws JMSException
   {
      return createTopicConnection(null, null);
   }

   public TopicConnection createTopicConnection(final String username, final String password) throws JMSException
   {
      return createConnectionInternal(username, password, false, HornetQConnection.TYPE_TOPIC_CONNECTION);
   }

   // XAConnectionFactory implementation -----------------------------------------------------------

   public XAConnection createXAConnection() throws JMSException
   {
      return createXAConnection(null, null);
   }

   public XAConnection createXAConnection(final String username, final String password) throws JMSException
   {
      return (XAConnection)createConnectionInternal(username, password, true, HornetQConnection.TYPE_GENERIC_CONNECTION);
   }

   // XAQueueConnectionFactory implementation ------------------------------------------------------

   public XAQueueConnection createXAQueueConnection() throws JMSException
   {
      return createXAQueueConnection(null, null);
   }

   public XAQueueConnection createXAQueueConnection(final String username, final String password) throws JMSException
   {
      return (XAQueueConnection)createConnectionInternal(username, password, true, HornetQConnection.TYPE_QUEUE_CONNECTION);
   }

   // XATopicConnectionFactory implementation ------------------------------------------------------

   public XATopicConnection createXATopicConnection() throws JMSException
   {
      return createXATopicConnection(null, null);
   }

   public XATopicConnection createXATopicConnection(final String username, final String password) throws JMSException
   {
      return (XATopicConnection)createConnectionInternal(username, password, true, HornetQConnection.TYPE_TOPIC_CONNECTION);
   }

   // Referenceable implementation -----------------------------------------------------------------

   public Reference getReference() throws NamingException
   {
      return new Reference(this.getClass().getCanonicalName(),
                           new SerializableObjectRefAddr("HornetQ-CF", this),
                           ConnectionFactoryObjectFactory.class.getCanonicalName(),
                           null);
   }

   // Public ---------------------------------------------------------------------------------------

   public boolean isHA()
   {
      return serverLocator.isHA();
   }

   public synchronized String getConnectionLoadBalancingPolicyClassName()
   {
      return serverLocator.getConnectionLoadBalancingPolicyClassName();
   }

   public synchronized void setConnectionLoadBalancingPolicyClassName(final String connectionLoadBalancingPolicyClassName)
   {
      checkWrite();
      serverLocator.setConnectionLoadBalancingPolicyClassName(connectionLoadBalancingPolicyClassName);
   }

   public synchronized TransportConfiguration[] getStaticConnectors()
   {
      return serverLocator.getStaticTransportConfigurations();
   }

   public synchronized DiscoveryGroupConfiguration getDiscoveryGroupConfiguration()
   {
      return serverLocator.getDiscoveryGroupConfiguration();
   }

   public synchronized String getClientID()
   {
      return clientID;
   }

   public synchronized void setClientID(final String clientID)
   {
      checkWrite();
      this.clientID = clientID;
   }

   public synchronized int getDupsOKBatchSize()
   {
      return dupsOKBatchSize;
   }

   public synchronized void setDupsOKBatchSize(final int dupsOKBatchSize)
   {
      checkWrite();
      this.dupsOKBatchSize = dupsOKBatchSize;
   }

   public synchronized int getTransactionBatchSize()
   {
      return transactionBatchSize;
   }

   public synchronized void setTransactionBatchSize(final int transactionBatchSize)
   {
      checkWrite();
      this.transactionBatchSize = transactionBatchSize;
   }

   public synchronized long getClientFailureCheckPeriod()
   {
      return serverLocator.getClientFailureCheckPeriod();
   }

   public synchronized void setClientFailureCheckPeriod(final long clientFailureCheckPeriod)
   {
      checkWrite();
      serverLocator.setClientFailureCheckPeriod(clientFailureCheckPeriod);
   }

   public synchronized long getConnectionTTL()
   {
      return serverLocator.getConnectionTTL();
   }

   public synchronized void setConnectionTTL(final long connectionTTL)
   {
      checkWrite();
      serverLocator.setConnectionTTL(connectionTTL);
   }

   public synchronized long getCallTimeout()
   {
      return serverLocator.getCallTimeout();
   }

   public synchronized void setCallTimeout(final long callTimeout)
   {
      checkWrite();
      serverLocator.setCallTimeout(callTimeout);
   }

   public synchronized int getConsumerWindowSize()
   {
      return serverLocator.getConsumerWindowSize();
   }

   public synchronized void setConsumerWindowSize(final int consumerWindowSize)
   {
      checkWrite();
      serverLocator.setConsumerWindowSize(consumerWindowSize);
   }

   public synchronized int getConsumerMaxRate()
   {
      return serverLocator.getConsumerMaxRate();
   }

   public synchronized void setConsumerMaxRate(final int consumerMaxRate)
   {
      checkWrite();
      serverLocator.setConsumerMaxRate(consumerMaxRate);
   }

   public synchronized int getConfirmationWindowSize()
   {
      return serverLocator.getConfirmationWindowSize();
   }

   public synchronized void setConfirmationWindowSize(final int confirmationWindowSize)
   {
      checkWrite();
      serverLocator.setConfirmationWindowSize(confirmationWindowSize);
   }

   public synchronized int getProducerMaxRate()
   {
      return serverLocator.getProducerMaxRate();
   }

   public synchronized void setProducerMaxRate(final int producerMaxRate)
   {
      checkWrite();
      serverLocator.setProducerMaxRate(producerMaxRate);
   }

   public synchronized int getProducerWindowSize()
   {
      return serverLocator.getProducerWindowSize();
   }

   public synchronized void setProducerWindowSize(final int producerWindowSize)
   {
      checkWrite();
      serverLocator.setProducerWindowSize(producerWindowSize);
   }

   /**
    * @param cacheLargeMessagesClient
    */
   public synchronized void setCacheLargeMessagesClient(final boolean cacheLargeMessagesClient)
   {
      checkWrite();
      serverLocator.setCacheLargeMessagesClient(cacheLargeMessagesClient);
   }

   public synchronized boolean isCacheLargeMessagesClient()
   {
      return serverLocator.isCacheLargeMessagesClient();
   }

   public synchronized int getMinLargeMessageSize()
   {
      return serverLocator.getMinLargeMessageSize();
   }

   public synchronized void setMinLargeMessageSize(final int minLargeMessageSize)
   {
      checkWrite();
      serverLocator.setMinLargeMessageSize(minLargeMessageSize);
   }

   public synchronized boolean isBlockOnAcknowledge()
   {
      return serverLocator.isBlockOnAcknowledge();
   }

   public synchronized void setBlockOnAcknowledge(final boolean blockOnAcknowledge)
   {
      checkWrite();
      serverLocator.setBlockOnAcknowledge(blockOnAcknowledge);
   }

   public synchronized boolean isBlockOnNonDurableSend()
   {
      return serverLocator.isBlockOnNonDurableSend();
   }

   public synchronized void setBlockOnNonDurableSend(final boolean blockOnNonDurableSend)
   {
      checkWrite();
      serverLocator.setBlockOnNonDurableSend(blockOnNonDurableSend);
   }

   public synchronized boolean isBlockOnDurableSend()
   {
      return serverLocator.isBlockOnDurableSend();
   }

   public synchronized void setBlockOnDurableSend(final boolean blockOnDurableSend)
   {
      checkWrite();
      serverLocator.setBlockOnDurableSend(blockOnDurableSend);
   }

   public synchronized boolean isAutoGroup()
   {
      return serverLocator.isAutoGroup();
   }

   public synchronized void setAutoGroup(final boolean autoGroup)
   {
      checkWrite();
      serverLocator.setAutoGroup(autoGroup);
   }

   public synchronized boolean isPreAcknowledge()
   {
      return serverLocator.isPreAcknowledge();
   }

   public synchronized void setPreAcknowledge(final boolean preAcknowledge)
   {
      checkWrite();
      serverLocator.setPreAcknowledge(preAcknowledge);
   }

   public synchronized long getRetryInterval()
   {
      return serverLocator.getRetryInterval();
   }

   public synchronized void setRetryInterval(final long retryInterval)
   {
      checkWrite();
      serverLocator.setRetryInterval(retryInterval);
   }

   public synchronized long getMaxRetryInterval()
   {
      return serverLocator.getMaxRetryInterval();
   }

   public synchronized void setMaxRetryInterval(final long retryInterval)
   {
      checkWrite();
      serverLocator.setMaxRetryInterval(retryInterval);
   }

   public synchronized double getRetryIntervalMultiplier()
   {
      return serverLocator.getRetryIntervalMultiplier();
   }

   public synchronized void setRetryIntervalMultiplier(final double retryIntervalMultiplier)
   {
      checkWrite();
      serverLocator.setRetryIntervalMultiplier(retryIntervalMultiplier);
   }

   public synchronized int getReconnectAttempts()
   {
      return serverLocator.getReconnectAttempts();
   }

   public synchronized void setReconnectAttempts(final int reconnectAttempts)
   {
      checkWrite();
      serverLocator.setReconnectAttempts(reconnectAttempts);
   }

   public synchronized void setInitialConnectAttempts(final int reconnectAttempts)
   {
      checkWrite();
      serverLocator.setInitialConnectAttempts(reconnectAttempts);
   }

   public synchronized int getInitialConnectAttempts()
   {
      checkWrite();
      return serverLocator.getInitialConnectAttempts();
   }

   public synchronized boolean isFailoverOnInitialConnection()
   {
      return serverLocator.isFailoverOnInitialConnection();
   }

   public synchronized void setFailoverOnInitialConnection(final boolean failover)
   {
      checkWrite();
      serverLocator.setFailoverOnInitialConnection(failover);
   }

   public synchronized boolean isUseGlobalPools()
   {
      return serverLocator.isUseGlobalPools();
   }

   public synchronized void setUseGlobalPools(final boolean useGlobalPools)
   {
      checkWrite();
      serverLocator.setUseGlobalPools(useGlobalPools);
   }

   public synchronized int getScheduledThreadPoolMaxSize()
   {
      return serverLocator.getScheduledThreadPoolMaxSize();
   }

   public synchronized void setScheduledThreadPoolMaxSize(final int scheduledThreadPoolMaxSize)
   {
      checkWrite();
      serverLocator.setScheduledThreadPoolMaxSize(scheduledThreadPoolMaxSize);
   }

   public synchronized int getThreadPoolMaxSize()
   {
      return serverLocator.getThreadPoolMaxSize();
   }

   public synchronized void setThreadPoolMaxSize(final int threadPoolMaxSize)
   {
      checkWrite();
      serverLocator.setThreadPoolMaxSize(threadPoolMaxSize);
   }

   public synchronized int getInitialMessagePacketSize()
   {
      return serverLocator.getInitialMessagePacketSize();
   }

   public synchronized void setInitialMessagePacketSize(final int size)
   {
      checkWrite();
      serverLocator.setInitialMessagePacketSize(size);
   }

   public void setGroupID(final String groupID)
   {
      serverLocator.setGroupID(groupID);
   }

   public String getGroupID()
   {
      return serverLocator.getGroupID();
   }

   public boolean isCompressLargeMessage()
   {
      return serverLocator.isCompressLargeMessage();
   }

   public void setCompressLargeMessage(boolean compress)
   {
      serverLocator.setCompressLargeMessage(compress);
   }

   public void close()
   {
      serverLocator.close();
   }

   public ServerLocator getServerLocator()
   {
      return serverLocator;
   }

   public int getFactoryType()
   {
      return JMSFactoryType.CF.intValue();
   }
   /**
    *
    * @deprecated use {@link ServerLocator#createSessionFactory()}
    * @return
    */
   @Deprecated
   public ClientSessionFactory getCoreFactory() throws JMSException
   {
      try
      {
         return serverLocator.createSessionFactory();
      }
      catch (Exception e)
      {
         JMSException ex = new JMSException (e.getMessage());
         ex.initCause(e);
         throw ex;
      }
   }

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   protected synchronized HornetQConnection createConnectionInternal(final String username,
                                                                     final String password,
                                                                     final boolean isXA,
                                                                     final int type) throws JMSException
   {
      readOnly = true;

      ClientSessionFactory factory;

      try
      {
         factory = serverLocator.createSessionFactory();
      }
      catch (Exception e)
      {
         JMSException jmse = new JMSException("Failed to create session factory");

         jmse.initCause(e);
         jmse.setLinkedException(e);

         throw jmse;
      }

      HornetQConnection connection = null;

      if (isXA)
      {
         if (type == HornetQConnection.TYPE_GENERIC_CONNECTION)
         {
            connection = new HornetQXAConnection(username,
                                                password,
                                                type,
                                                clientID,
                                                dupsOKBatchSize,
                                                transactionBatchSize,
                                                factory);
         }
         else if (type == HornetQConnection.TYPE_QUEUE_CONNECTION)
         {
            connection = new HornetQXAQueueConnection(username,
                                                      password,
                                                      type,
                                                      clientID,
                                                      dupsOKBatchSize,
                                                      transactionBatchSize,
                                                      factory);
         }
         else if (type == HornetQConnection.TYPE_TOPIC_CONNECTION)
         {
            connection = new HornetQXATopicConnection(username,
                                                      password,
                                                      type,
                                                      clientID,
                                                      dupsOKBatchSize,
                                                      transactionBatchSize,
                                                      factory);
         }
      }
      else
      {
         if (type == HornetQConnection.TYPE_GENERIC_CONNECTION)
         {
            connection = new HornetQConnection(username,
                                               password,
                                               type,
                                               clientID,
                                               dupsOKBatchSize,
                                               transactionBatchSize,
                                               factory);
         }
         else if (type == HornetQConnection.TYPE_QUEUE_CONNECTION)
         {
            connection = new HornetQQueueConnection(username,
                                                    password,
                                                    type,
                                                    clientID,
                                                    dupsOKBatchSize,
                                                    transactionBatchSize,
                                                    factory);
         }
         else if (type == HornetQConnection.TYPE_TOPIC_CONNECTION)
         {
            connection = new HornetQTopicConnection(username,
                                                    password,
                                                    type,
                                                    clientID,
                                                    dupsOKBatchSize,
                                                    transactionBatchSize,
                                                    factory);
         }
      }

      if (connection == null)
      {
         throw new JMSException("Failed to create connection: invalid type " + type);
      }
      connection.setReference(this);

      try
      {
         connection.authorize();
      }
      catch (JMSException e)
      {
         try
         {
            connection.close();
         }
         catch (JMSException me)
         {
         }
         throw e;
      }

      return connection;
   }

   // Private --------------------------------------------------------------------------------------

   private void checkWrite()
   {
      if (readOnly)
      {
         throw new IllegalStateException("Cannot set attribute on HornetQConnectionFactory after it has been used");
      }
   }

   @Override
   public void finalize() throws Throwable
   {
      try
      {
         serverLocator.close();
      }
      catch (Exception e)
      {
         e.printStackTrace();
         //not much we can do here
      }
      super.finalize();
   }
   // Inner classes --------------------------------------------------------------------------------

}