package io.github.kurobako.agave;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static io.github.kurobako.agave.Dictionary.dictionary;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(Parameterized.class)
public class DictionaryTest {

  @Parameterized.Parameter
  public Dictionary<String, String> dict;

  @Test
  public void testLookupDelete() {
    for (int i = 0; i < dict.size(); i++) {
      String s = String.valueOf(i);
      assertEquals(s, dict.lookup(s).asNullable());
      Dictionary<String, String> without = dict.delete(s);
      assertNull(without.lookup(s).asNullable());
      assertEquals(dict, without.insert(s, s));
    }
  }

  @Test
  public void testInsertLookup() {
    Dictionary<String, String> d = dict.insert("ababa", "ababa");
    assertEquals("ababa", d.lookup("ababa").asNullable());
  }

  @Test
  public void testInsertLookupCollision() {
    Dictionary<String, String> d = dict.insert("FB", "FB");
    d = d.insert("Ea", "Ea");
    assertEquals("FB", d.lookup("FB").asNullable());
    assertEquals("Ea", d.lookup("Ea").asNullable());
  }

  @Parameterized.Parameters
  public static Collection<Dictionary<String, String>> data() {
    final List<Dictionary<String, String>> result = new ArrayList<>();
    result.add(dictionary());
    result.add(dictionary("0", "0"));
    Dictionary<String, String> ju = dictionary();
    for (int i = 0; i < 10; i++) ju = ju.insert(String.valueOf(i), String.valueOf(i));
    result.add(ju);
    Dictionary<String, String> hyaku = dictionary();
    for (int i = 0; i < 100; i++) hyaku = hyaku.insert(String.valueOf(i), String.valueOf(i));
    result.add(hyaku);
    Dictionary<String, String> sen = dictionary();
    for (int i = 0; i < 1000; i++) sen = sen.insert(String.valueOf(i), String.valueOf(i));
    result.add(sen);
    Dictionary<String, String> man = dictionary();
    for (int i = 0; i < 10000; i++) man = man.insert(String.valueOf(i), String.valueOf(i));
    result.add(man);
    return result;
  }
}
