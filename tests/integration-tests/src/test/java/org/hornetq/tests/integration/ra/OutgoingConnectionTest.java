/*
 * Copyright 2005-2014 Red Hat, Inc.
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
package org.hornetq.tests.integration.ra;

import javax.jms.Connection;
import javax.jms.IllegalStateException;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.JMSSecurityException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.TopicConnection;
import javax.jms.XAConnection;
import javax.jms.XAQueueConnection;
import javax.jms.XASession;
import javax.resource.spi.ManagedConnection;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.util.HashSet;
import java.util.Set;

import org.hornetq.api.core.client.ClientConsumer;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.jms.HornetQJMSClient;
import org.hornetq.core.remoting.impl.invm.InVMConnectorFactory;
import org.hornetq.core.security.Role;
import org.hornetq.core.transaction.impl.XidImpl;
import org.hornetq.ra.HornetQRAConnectionFactory;
import org.hornetq.ra.HornetQRAConnectionFactoryImpl;
import org.hornetq.ra.HornetQRAConnectionManager;
import org.hornetq.ra.HornetQRAManagedConnectionFactory;
import org.hornetq.ra.HornetQRASession;
import org.hornetq.ra.HornetQResourceAdapter;
import org.hornetq.utils.UUIDGenerator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 *         Created Jul 7, 2010
 */
public class OutgoingConnectionTest extends HornetQRATestBase
{
   private HornetQResourceAdapter resourceAdapter;
   private HornetQRAConnectionFactory qraConnectionFactory;
   private HornetQRAManagedConnectionFactory mcf;

   @Override
   public boolean useSecurity()
   {
      return true;
   }

   HornetQRAConnectionManager qraConnectionManager = new HornetQRAConnectionManager();

   @Override
   @Before
   public void setUp() throws Exception
   {
      super.setUp();
      server.getSecurityManager().addUser("testuser", "testpassword");
      server.getSecurityManager().addUser("guest", "guest");
      server.getSecurityManager().setDefaultUser("guest");
      server.getSecurityManager().addRole("testuser", "arole");
      server.getSecurityManager().addRole("guest", "arole");
      Role role = new Role("arole", true, true, true, true, true, true, true);
      Set<Role> roles = new HashSet<Role>();
      roles.add(role);
      server.getSecurityRepository().addMatch(MDBQUEUEPREFIXED, roles);

      resourceAdapter = new HornetQResourceAdapter();
      resourceAdapter.setTransactionManagerLocatorClass(JMSContextTest.class.getName());
      resourceAdapter.setTransactionManagerLocatorMethod("getTm");

      resourceAdapter.setConnectorClassName(InVMConnectorFactory.class.getName());
      MyBootstrapContext ctx = new MyBootstrapContext();
      resourceAdapter.start(ctx);
      mcf = new HornetQRAManagedConnectionFactory();
      mcf.setResourceAdapter(resourceAdapter);
      qraConnectionFactory = new HornetQRAConnectionFactoryImpl(mcf, qraConnectionManager);
   }

   @Override
   @After
   public void tearDown() throws Exception
   {
      DummyTransactionManager.tm.tx = null;
      if (resourceAdapter != null)
      {
         resourceAdapter.stop();
      }

      qraConnectionManager.stop();
      super.tearDown();
   }

   @Test
   public void testSimpleMessageSendAndReceive() throws Exception
   {
      QueueConnection queueConnection = qraConnectionFactory.createQueueConnection();
      Session s = queueConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Queue q = HornetQJMSClient.createQueue(MDBQUEUE);
      MessageProducer mp = s.createProducer(q);
      MessageConsumer consumer = s.createConsumer(q);
      Message message = s.createTextMessage("test");
      mp.send(message);
      queueConnection.start();
      TextMessage textMessage = (TextMessage) consumer.receive(1000);
      assertNotNull(textMessage);
      assertEquals(textMessage.getText(), "test");
   }

   @Test
   public void testSimpleSendNoXAJMSContext() throws Exception
   {
      Queue q = HornetQJMSClient.createQueue(MDBQUEUE);

      try(ClientSessionFactory sf = locator.createSessionFactory();
          ClientSession session = sf.createSession();
          ClientConsumer consVerify = session.createConsumer("jms.queue." + MDBQUEUE);
          JMSContext jmsctx = qraConnectionFactory.createContext();
      )
      {
         session.start();
         // These next 4 lines could be written in a single line however it makes difficult for debugging
         JMSProducer producer = jmsctx.createProducer();
         producer.setProperty("strvalue", "hello");
         TextMessage msgsend = jmsctx.createTextMessage("hello");
         producer.send(q, msgsend);

         ClientMessage msg = consVerify.receive(1000);
         assertNotNull(msg);
         assertEquals("hello", msg.getStringProperty("strvalue"));
      }
   }

   @Test
   public void testSimpleSendNoXAJMS1() throws Exception
   {
      Queue q = HornetQJMSClient.createQueue(MDBQUEUE);
      try(ClientSessionFactory sf = locator.createSessionFactory();
          ClientSession session = sf.createSession();
          ClientConsumer consVerify = session.createConsumer("jms.queue." + MDBQUEUE);
          Connection conn = qraConnectionFactory.createConnection();
      )
      {
         Session jmsSess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         session.start();
         MessageProducer producer = jmsSess.createProducer(q);
         // These next 4 lines could be written in a single line however it makes difficult for debugging
         TextMessage msgsend = jmsSess.createTextMessage("hello");
         msgsend.setStringProperty("strvalue", "hello");
         producer.send(msgsend);

         ClientMessage msg = consVerify.receive(1000);
         assertNotNull(msg);
         assertEquals("hello", msg.getStringProperty("strvalue"));
      }
   }

   @Test
   public void testSimpleMessageSendAndReceiveXA() throws Exception
   {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      XAQueueConnection queueConnection = qraConnectionFactory.createXAQueueConnection();
      XASession s = queueConnection.createXASession();

      XAResource resource = s.getXAResource();
      resource.start(xid, XAResource.TMNOFLAGS);
      Queue q = HornetQJMSClient.createQueue(MDBQUEUE);
      MessageProducer mp = s.createProducer(q);
      MessageConsumer consumer = s.createConsumer(q);
      Message message = s.createTextMessage("test");
      mp.send(message);
      queueConnection.start();
      TextMessage textMessage = (TextMessage) consumer.receiveNoWait();
      assertNull(textMessage);
      resource.end(xid, XAResource.TMSUCCESS);
      resource.commit(xid, true);
      resource.start(xid, XAResource.TMNOFLAGS);
      textMessage = (TextMessage) consumer.receiveNoWait();
      resource.end(xid, XAResource.TMSUCCESS);
      resource.commit(xid, true);
      assertNotNull(textMessage);
      assertEquals(textMessage.getText(), "test");

      // When I wrote this call, this method was doing an infinite loop.
      // this is just to avoid such thing again
      textMessage.getJMSDeliveryTime();

   }

   @Test
   public void testInexistentUserOnCreateConnection() throws Exception
   {
      resourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      resourceAdapter.start(ctx);
      HornetQRAManagedConnectionFactory mcf = new HornetQRAManagedConnectionFactory();
      mcf.setResourceAdapter(resourceAdapter);
      HornetQRAConnectionFactory qraConnectionFactory = new HornetQRAConnectionFactoryImpl(mcf, qraConnectionManager);

      Connection conn  = null;
      try
      {
         conn = qraConnectionFactory.createConnection("IDont", "Exist");
         fail("Exception was expected");
      }
      catch (JMSSecurityException expected)
      {
      }

      conn = qraConnectionFactory.createConnection("testuser", "testpassword");
      conn.close();


      try
      {
         XAConnection xaconn = qraConnectionFactory.createXAConnection("IDont", "Exist");
         fail("Exception was expected");
      }
      catch (JMSSecurityException expected)
      {
      }

      XAConnection xaconn = qraConnectionFactory.createXAConnection("testuser", "testpassword");
      xaconn.close();

      try
      {
         TopicConnection topicconn = qraConnectionFactory.createTopicConnection("IDont", "Exist");
         fail("Exception was expected");
      }
      catch (JMSSecurityException expected)
      {
      }

      TopicConnection topicconn = qraConnectionFactory.createTopicConnection("testuser", "testpassword");
      topicconn.close();

      try
      {
         QueueConnection queueconn = qraConnectionFactory.createQueueConnection("IDont", "Exist");
         fail("Exception was expected");
      }
      catch (JMSSecurityException expected)
      {
      }

      QueueConnection queueconn = qraConnectionFactory.createQueueConnection("testuser", "testpassword");
      queueconn.close();

      mcf.stop();


   }

   @Test
   public void testSimpleMessageSendAndReceiveTransacted() throws Exception
   {
      setupDLQ(10);
      resourceAdapter = newResourceAdapter();
      resourceAdapter.setTransactionManagerLocatorClass(JMSContextTest.class.getName());
      resourceAdapter.setTransactionManagerLocatorMethod("getTm");
      DummyTransactionManager.tm.tx = new DummyTransaction();
      MyBootstrapContext ctx = new MyBootstrapContext();
      resourceAdapter.start(ctx);
      HornetQRAManagedConnectionFactory mcf = new HornetQRAManagedConnectionFactory();
      mcf.setResourceAdapter(resourceAdapter);
      HornetQRAConnectionFactory qraConnectionFactory = new HornetQRAConnectionFactoryImpl(mcf, qraConnectionManager);
      QueueConnection queueConnection = qraConnectionFactory.createQueueConnection();
      Session s = queueConnection.createSession(true, Session.AUTO_ACKNOWLEDGE);
      Queue q = HornetQJMSClient.createQueue(MDBQUEUE);
      MessageProducer mp = s.createProducer(q);
      MessageConsumer consumer = s.createConsumer(q);
      Message message = s.createTextMessage("test");
      mp.send(message);
      s.commit();
      queueConnection.start();
      TextMessage textMessage = (TextMessage) consumer.receive(1000);
      assertNotNull(textMessage);
      assertEquals(textMessage.getText(), "test");
      s.rollback();
      textMessage = (TextMessage) consumer.receive(1000);
      assertNotNull(textMessage);
      assertEquals(textMessage.getText(), "test");
      s.commit();
   }

   @Test
   public void testMultipleSessionsThrowsException() throws Exception
   {
      resourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      resourceAdapter.start(ctx);
      HornetQRAManagedConnectionFactory mcf = new HornetQRAManagedConnectionFactory();
      mcf.setResourceAdapter(resourceAdapter);
      HornetQRAConnectionFactory qraConnectionFactory = new HornetQRAConnectionFactoryImpl(mcf, qraConnectionManager);
      QueueConnection queueConnection = qraConnectionFactory.createQueueConnection();
      Session s = queueConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      try
      {
         Session s2 = queueConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         fail("should throw javax,jms.IllegalStateException: Only allowed one session per connection. See the J2EE spec, e.g. J2EE1.4 Section 6.6");
      }
      catch (JMSException e)
      {
      }
   }

   @Test
   public void testConnectionCredentials() throws Exception
   {
      resourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      resourceAdapter.start(ctx);
      HornetQRAManagedConnectionFactory mcf = new HornetQRAManagedConnectionFactory();
      mcf.setResourceAdapter(resourceAdapter);
      HornetQRAConnectionFactory qraConnectionFactory = new HornetQRAConnectionFactoryImpl(mcf, qraConnectionManager);
      QueueConnection queueConnection = qraConnectionFactory.createQueueConnection();
      QueueSession session = queueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);

      queueConnection = qraConnectionFactory.createQueueConnection("testuser", "testpassword");
      session = queueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);

   }

   @Test
   public void testConnectionCredentialsFail() throws Exception
   {
      resourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      resourceAdapter.start(ctx);
      HornetQRAManagedConnectionFactory mcf = new HornetQRAManagedConnectionFactory();
      mcf.setResourceAdapter(resourceAdapter);
      HornetQRAConnectionFactory qraConnectionFactory = new HornetQRAConnectionFactoryImpl(mcf, qraConnectionManager);
      QueueConnection queueConnection = qraConnectionFactory.createQueueConnection();
      QueueSession session = queueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);

      ManagedConnection mc = ((HornetQRASession)session).getManagedConnection();
      queueConnection.close();
      mc.destroy();

      try
      {
         queueConnection = qraConnectionFactory.createQueueConnection("testuser", "testwrongpassword");
         queueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE).close();
         fail("should throw esxception");
      }
      catch (JMSException e)
      {
         //pass
      }
   }

   @Test
   public void testConnectionCredentialsFailRecovery() throws Exception
   {
      resourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      resourceAdapter.start(ctx);
      HornetQRAManagedConnectionFactory mcf = new HornetQRAManagedConnectionFactory();
      mcf.setResourceAdapter(resourceAdapter);
      HornetQRAConnectionFactory qraConnectionFactory = new HornetQRAConnectionFactoryImpl(mcf, qraConnectionManager);
      try
      {
         QueueConnection queueConnection = qraConnectionFactory.createQueueConnection("testuser", "testwrongpassword");
         queueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE).close();
         fail("should throw esxception");
      }
      catch (JMSException e)
      {
         //make sure the recovery is null
         assertNull(mcf.getResourceRecovery());
      }
   }

   @Test
   public void testConnectionCredentialsOKRecovery() throws Exception
   {
      resourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      resourceAdapter.start(ctx);
      HornetQRAManagedConnectionFactory mcf = new HornetQRAManagedConnectionFactory();
      mcf.setResourceAdapter(resourceAdapter);
      HornetQRAConnectionFactory qraConnectionFactory = new HornetQRAConnectionFactoryImpl(mcf, qraConnectionManager);
      QueueConnection queueConnection = qraConnectionFactory.createQueueConnection();
      QueueSession session = queueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);

      assertNotNull(mcf.getResourceRecovery());
   }

   @Test
   public void testJMSContext() throws Exception
   {
      resourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      resourceAdapter.start(ctx);
      HornetQRAManagedConnectionFactory mcf = new HornetQRAManagedConnectionFactory();
      mcf.setResourceAdapter(resourceAdapter);
      HornetQRAConnectionFactory qraConnectionFactory = new HornetQRAConnectionFactoryImpl(mcf, qraConnectionManager);

      JMSContext jmsctx = qraConnectionFactory.createContext(JMSContext.DUPS_OK_ACKNOWLEDGE);
      assertEquals(JMSContext.DUPS_OK_ACKNOWLEDGE, jmsctx.getSessionMode());

   }


   @Test
   public void testQueueSessionAckModeJTA() throws Exception
   {
      testQueuSessionAckMode(true);
   }

   @Test
   public void testQueueSessionAckModeNoJTA() throws Exception
   {
      testQueuSessionAckMode(false);
   }

   public void testQueuSessionAckMode(boolean inTx) throws Exception
   {
      if(inTx)
      {
         DummyTransactionManager.tm.tx = new DummyTransaction();
      }
      QueueConnection queueConnection = qraConnectionFactory.createQueueConnection();

      Session s = queueConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      if(inTx)
      {
         assertEquals(Session.SESSION_TRANSACTED, s.getAcknowledgeMode());
      }
      else
      {
         assertEquals(Session.AUTO_ACKNOWLEDGE, s.getAcknowledgeMode());
      }
      s.close();

      s = queueConnection.createSession(false, Session.DUPS_OK_ACKNOWLEDGE);
      if(inTx)
      {
         assertEquals(Session.SESSION_TRANSACTED, s.getAcknowledgeMode());
      }
      else
      {
         assertEquals(Session.DUPS_OK_ACKNOWLEDGE, s.getAcknowledgeMode());
      }
      s.close();

      //exception should be thrown if ack mode is SESSION_TRANSACTED or
      //CLIENT_ACKNOWLEDGE when in a JTA else ackmode should bee ignored
      try
      {
         s = queueConnection.createSession(false, Session.SESSION_TRANSACTED);
         if(inTx)
         {
            assertEquals(s.getAcknowledgeMode(), Session.SESSION_TRANSACTED);
         }
         else
         {
            fail("didn't get expected exception creating session with SESSION_TRANSACTED mode ");
         }
         s.close();
      }
      catch (JMSException e)
      {
         if(inTx)
         {
            fail("shouldn't throw exception " + e);
         }
      }
      
      try
      {
         s = queueConnection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         if(inTx)
         {
            assertEquals(s.getAcknowledgeMode(), Session.SESSION_TRANSACTED);
         }
         else
         {
            fail("didn't get expected exception creating session with CLIENT_ACKNOWLEDGE mode");
         }
      }
      catch (JMSException e)
      {
         if(inTx)
         {
            fail("shouldn't throw exception " + e);
         }
      }
      
   }
}
