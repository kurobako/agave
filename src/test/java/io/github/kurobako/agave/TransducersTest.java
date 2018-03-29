package io.github.kurobako.agave;

import org.junit.Test;

import static io.github.kurobako.agave.Sequence.sequence;
import static io.github.kurobako.agave.Transducers.dedupe;
import static io.github.kurobako.agave.Transducers.drop;
import static io.github.kurobako.agave.Transducers.filter;
import static io.github.kurobako.agave.Transducers.map;
import static io.github.kurobako.agave.Transducers.take;
import static org.junit.Assert.assertEquals;

public class TransducersTest {

  private final Sequence<Character> seq = sequence('a', 'b', 'c', 'd');

  @Test
  public void testFilter() {
    assertEquals("abd", seq.transduceLeft(filter(s -> !s.equals('c')), (s, c) -> s + c, ""));
    assertEquals("dba", seq.transduceRight(filter(s -> !s.equals('c')), (c, s) -> s + c, ""));
  }

  @Test
  public void testMap() {
    assertEquals("ABCD", seq.transduceLeft(map(c -> c.toString().toUpperCase()), (s, c) -> s + c, ""));
    assertEquals("DCBA", seq.transduceRight(map(c -> c.toString().toUpperCase()), (c, s) -> s + c, ""));
  }

  @Test
  public void testTake() {
    assertEquals("ab", seq.transduceLeft(take(2), (s, c) -> s + c, ""));
    assertEquals("dc", seq.transduceRight(take(2), (c, s) -> s + c, ""));
  }

  @Test
  public void testDrop() {
    assertEquals("cd", seq.transduceLeft(drop(2), (s, c) -> s + c, ""));
    assertEquals("ba", seq.transduceRight(drop(2), (c, s) -> s + c, ""));
  }

  @Test
  public void testDedupe() {
    final Sequence<Character> duped = seq.flatMap(c -> sequence(c, c));
    assertEquals("abcd", seq.transduceLeft(dedupe(), (s, c) -> s + c, ""));
    assertEquals("dcba", seq.transduceRight(dedupe(), (c, s) -> s + c, ""));
  }
}
