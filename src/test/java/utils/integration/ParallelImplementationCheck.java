package utils.integration;

import org.junit.jupiter.api.Test;
import utils.HttpGet;

public class ParallelImplementationCheck {
  @Test
  public void runParallelCheck() throws Exception {
    Thread threads[] = new Thread[10];
    for(int i=0 ; i<10 ; i++) {
      threads[i] = new Thread(() -> {
        try {
          String content = HttpGet.run("http://localhost:12346/test?wait=1000&loops=10");
          System.out.println(content);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      });
    }
    long start = System.currentTimeMillis();
    for(int i=0 ; i<10 ; i++) {
      threads[i].start();
    }
    for(int i=0 ; i<10 ; i++) {
      threads[i].join();
    }
    System.out.println("// test took " + (System.currentTimeMillis() - start));
  }
}
