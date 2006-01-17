/*
* JBoss, Home of Professional Open Source
* Copyright 2005, JBoss Inc., and individual contributors as indicated
* by the @authors tag. See the copyright.txt in the distribution for a
* full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/
package org.jboss.test.messaging.core;

import org.jboss.messaging.core.DeliveryObserver;
import org.jboss.messaging.core.Delivery;
import org.jboss.messaging.core.tx.Transaction;
import org.jboss.logging.Logger;

/**
 * @author <a href="mailto:ovidiu@jboss.org">Ovidiu Feodorov</a>
 * @version <tt>$Revision$</tt>
 * $Id$
 */
public class SimpleDeliveryObserver implements DeliveryObserver
{
   // Constants -----------------------------------------------------

   private static final Logger log = Logger.getLogger(SimpleDeliveryObserver.class);

   // Static --------------------------------------------------------
   
   // Attributes ----------------------------------------------------

   protected Delivery toBeCancelled;
   protected Delivery toBeAcknowledged;

   // Constructors --------------------------------------------------

   // DeliveryObserver implementation --------------------------

   public synchronized void acknowledge(Delivery d, Transaction tx)
   {
      log.info("Delivery " + d + " was acknowledged");
      if (toBeAcknowledged == d)
      {
         toBeAcknowledged = null;
         notifyAll();
      }
   }

   public synchronized void cancel(Delivery d)
   {
      log.info("Delivery " + d + " was cancelled");
      if (toBeCancelled == d)
      {
         toBeCancelled = null;
         notifyAll();
      }
      return;
   }

   // Public --------------------------------------------------------

   public synchronized void waitForCancellation(Delivery delivery) throws Exception
   {
      waitForCancellation(delivery, 0);
   }

   /**
    * Waits until the delivery is cancelled, or timeout expires. If the delivery is already
    * cancelled, exits immediately.
    */
   public synchronized boolean waitForCancellation(Delivery delivery, long timeout) throws Exception
   {
      try
      {
         if (delivery.isCancelled())
         {
            log.info("the delivery already cancelled, exiting");
            return true;
         }

         if (toBeCancelled != null)
         {
            throw new IllegalStateException("already waiting for another delivery cancellation");
         }

         toBeCancelled = delivery;

         if (timeout <= 0)
         {
            this.wait();
         }
         else
         {
            this.wait(timeout);
         }

         if (toBeCancelled == null)
         {
            log.info("delivery was cancelled in time, no timeout");
            return true;
         }
         else
         {
            log.warn("exiting on timeout");
            return false;
         }
      }
      finally
      {
         toBeCancelled = null;
      }
   }

   public synchronized boolean waitForAcknowledgment(Delivery delivery) throws Exception
   {
      return waitForAcknowledgment(delivery, 0);
   }

   /**
    * Waits until the delivery is positively acknowledged (done), or timeout expires. If the
    * delivery is already done, exits immediately.
    * @return true if a positive acknowledgment was received before or during the method call,
    *         false if the method exists with timeout.
    */
   public synchronized boolean waitForAcknowledgment(Delivery delivery, long timeout)
         throws Exception
   {
      try
      {
         if (delivery.isDone())
         {
            log.info("the delivery already acknowledged, exiting");
            return true;
         }

         if (toBeAcknowledged != null)
         {
            throw new IllegalStateException("already waiting for another delivery acknowlegment");
         }

         toBeAcknowledged = delivery;

         if (timeout <= 0)
         {
            this.wait();
         }
         else
         {
            this.wait(timeout);
         }

         if (toBeAcknowledged == null)
         {
            log.info("delivery was acknowledged in time, no timeout");
            return true;
         }
         else
         {
            log.warn("exiting on timeout");
            return false;
         }
      }
      finally
      {
         toBeAcknowledged = null;
      }
   }

   // Package protected ---------------------------------------------
   
   // Protected -----------------------------------------------------
   
   // Private -------------------------------------------------------
   
   // Inner classes -------------------------------------------------
}
