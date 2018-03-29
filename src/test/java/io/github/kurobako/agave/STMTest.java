package io.github.kurobako.agave;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class STMTest {

  @Test
  public void test() throws InterruptedException {
    final int n = 10;
    STM stm = new STM();
    List<STM.Ref<Integer>> refs = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      refs.add(stm.ref(i));
    }
    ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    Random r = new Random();
    for (int i = 0; i < n * 500000; i++) {
      executorService.submit(() -> {
        STM.Ref<Integer> fst = refs.get(r.nextInt(n));
        STM.Ref<Integer> snd = refs.get(r.nextInt(n));
        fst.transactionally(fstVal -> {
          int sndVal = snd.deref();
          snd.assign(fstVal);
          return fst.assign(sndVal);
        });
      });
    }
    executorService.shutdown();
    executorService.awaitTermination(1, TimeUnit.DAYS);
    Set<Integer> result = new HashSet<>();
    for (int i = 0; i < n; i++) {
      result.add(refs.get(i).deref());
    }
    assertEquals(n, result.size());
  }
}
