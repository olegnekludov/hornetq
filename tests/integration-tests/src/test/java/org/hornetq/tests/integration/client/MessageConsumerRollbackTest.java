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
package org.hornetq.tests.integration.client;
import org.junit.Before;
import org.junit.After;

import org.junit.Test;

import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.JMSException;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.client.ClientConsumer;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientProducer;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.MessageHandler;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.core.settings.impl.AddressSettings;
import org.hornetq.jms.client.HornetQTextMessage;
import org.hornetq.tests.util.ServiceTestBase;

/**
 * A MessageConsumerRollbackTest
 *
 * @author clebert
 *
 *
 */
public class MessageConsumerRollbackTest extends ServiceTestBase
{

   HornetQServer server;

   ServerLocator locator;

   ClientSessionFactory factory;

   private static final String inQueue = "inqueue";

   private static final String outQueue = "outQueue";

   @Before
   public void setUp() throws Exception
   {
      super.setUp();

      server = createServer(true, true);

      AddressSettings settings = new AddressSettings();
      settings.setRedeliveryDelay(100);
      server.getConfiguration().getAddressesSettings().put("#", settings);

      server.start();

      locator = createNettyNonHALocator();

      factory = createSessionFactory(locator);

      ClientSession session = factory.createTransactedSession();

      session.createQueue(inQueue, inQueue, true);

      session.createQueue(outQueue, outQueue, true);

      session.close();
   }

   @After
   public void tearDown() throws Exception
   {
      try
      {
         factory.close();
         locator.close();
      }
      catch (Exception ignored)
      {
      }

      server.stop();

      super.tearDown();
   }

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   @Test
   public void testRollbackMultipleConsumers() throws Exception
   {

      int numberOfMessages = 3000;
      int numberOfConsumers = 10;

      ClientSession session = factory.createTransactedSession();

      sendMessages(numberOfMessages, session);

      AtomicInteger count = new AtomicInteger(0);
      CountDownLatch commitLatch = new CountDownLatch (numberOfMessages);

      LocalConsumer[] consumers = new LocalConsumer[numberOfConsumers];

      for (int i = 0 ; i < numberOfConsumers; i++)
      {
         consumers[i] = new LocalConsumer(count, commitLatch);
         consumers[i].start();
      }


      commitLatch.await(2, TimeUnit.MINUTES);


      for (LocalConsumer consumer : consumers)
      {
         consumer.stop();
      }


      ClientConsumer consumer = session.createConsumer(outQueue);

      session.start();

      HashSet<Integer> values = new HashSet<Integer>();

      for (int i = 0 ; i < numberOfMessages; i++)
      {
         ClientMessage msg = consumer.receive(1000);
         assertNotNull(msg);
         int value = msg.getIntProperty("out_msg");
         msg.acknowledge();
         assertFalse("msg " + value + " received in duplicate", values.contains(value));
         values.add(value);
      }


      assertNull(consumer.receiveImmediate());

      for (int i = 0 ; i < numberOfMessages; i++)
      {
         assertTrue(values.contains(i));
      }

      assertEquals(numberOfMessages, values.size());

      session.close();

   }

   /**
    * @param numberOfMessages
    * @param session
    * @throws HornetQException
    * @throws JMSException
    * @throws Exception
    */
   private void sendMessages(int numberOfMessages, ClientSession session) throws HornetQException,
                                                                         JMSException,
                                                                         Exception
   {
      ClientProducer producer = session.createProducer(inQueue);

      for (int i = 0; i < numberOfMessages; i++)
      {
         HornetQTextMessage txt = new HornetQTextMessage(session);
         txt.setIntProperty("msg", i);
         txt.setText("Message Number (" + i + ")");
         txt.doBeforeSend();
         producer.send(txt.getCoreMessage());
      }

      session.commit();
   }

   private class LocalConsumer implements MessageHandler
   {

      // One of the tests will need this
      boolean rollbackFirstMessage = true;

      ServerLocator consumerLocator;

      ClientSessionFactory factoryLocator;

      ClientSession session;

      ClientConsumer consumer;

      ClientProducer producer;

      AtomicInteger counter;

      CountDownLatch commitLatch;

      public LocalConsumer(AtomicInteger counter, CountDownLatch commitLatch)
      {
         this.counter = counter;
         this.commitLatch = commitLatch;
      }

      public void stop() throws Exception
      {
         session.close();
         factoryLocator.close();
         consumerLocator.close();
      }

      public void start() throws Exception
      {
         consumerLocator = createNettyNonHALocator();

         factoryLocator = createSessionFactory(consumerLocator);

         session = factoryLocator.createTransactedSession();

         consumer = session.createConsumer(inQueue);

         producer = session.createProducer(outQueue);

         consumer.setMessageHandler(this);

         session.start();
      }

      public void onMessage(ClientMessage message)
      {

         try
         {

            message.acknowledge();
            ClientMessage outmsg = session.createMessage(true);

            outmsg.putIntProperty("out_msg", message.getIntProperty("msg"));

            producer.send(outmsg);


            if (rollbackFirstMessage)
            {
               session.rollback();
               rollbackFirstMessage = false;
               return;
            }

            if (counter.incrementAndGet() % 200 == 0)
            {
               System.out.println("rollback " + message);
               session.rollback();
            }
            else
            {
               commitLatch.countDown();
               session.commit();
            }
         }
         catch (Exception e)
         {
            e.printStackTrace();
            try
            {
               session.rollback();
            }
            catch (Exception ignored)
            {
               ignored.printStackTrace();
            }
         }

      }
   }
}
