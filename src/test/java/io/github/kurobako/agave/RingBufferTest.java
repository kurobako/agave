package io.github.kurobako.agave;

import io.github.kurobako.agave.ringbuffer.Consume;
import io.github.kurobako.agave.ringbuffer.Consumer;
import io.github.kurobako.agave.ringbuffer.RingBuffer;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class RingBufferTest {

  @Test
  public void testRB() throws InterruptedException {
    final int LIMIT = 1024*1024;
    final AtomicInteger i = new AtomicInteger(0);
    final RingBuffer<Integer> buffer = RingBuffer.multiProducer(1000);
    Runnable publish = () -> {
      while (true) {
        final int v = i.getAndIncrement();
        if (v >= LIMIT) break;
        long token = buffer.claim();
        buffer.write(token, v);
        buffer.publish(token);
      }
    };
    Thread firstPublisher = new Thread(publish);
    Thread secondPublisher = new Thread(publish);
    final int[] array = new int[LIMIT];
    Consumer<Integer> consumer = buffer.subscribe();
    Consume<Integer> consume = (data, more) -> {
      assertEquals(0, array[data]);
      array[data] = data;
      if (data % 10000 == 0) System.out.println("Consumed " + data);
      return true;
    };
    firstPublisher.start();
    secondPublisher.start();
    Thread.sleep(1000);
    Consumer.State state;
    do {
      state = consumer.consume(consume);
      Thread.sleep(100);
    } while (state != Consumer.State.IDLE);
    firstPublisher.join();
    secondPublisher.join();
    for (int j = 0; j < LIMIT; j++) {
      assertEquals(j, array[j]);
    }
  }
}
