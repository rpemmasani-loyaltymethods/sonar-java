import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.Writer;
import java.util.Formatter;

class A {
  private enum MyEnum {
    A, B, C;
  }

  private final static int MAX_LOOP = 42;
  private BufferedReader br;

  void closeable_never_closed() throws Exception {
    Reader reader = new FileReader(""); // Noncompliant - not used and not closed

    Writer writer = new FileWriter(""); // Noncompliant - not closed
    writer.write(10);

    InputStream is;
    is = new FileInputStream(""); // Compliant
    is.close();
    is = new FileInputStream(""); // Noncompliant - reinitialized and not closed

    RandomAccessFile raf = new RandomAccessFile("", "r"); // Noncompliant - reinitialized before closing
    raf.length();
    raf = new RandomAccessFile("", "r"); // Compliant
    raf.close();
  }

  Closeable closeable_never_closed_but_state_is_lost(Closeable closeable) throws Exception {
    Formatter formatter = new Formatter(); // Compliant - (unknown) as Closeable instance is later used as method parameter
    myMethod(formatter);

    FileInputStream fis = getFileInputStream(); // Compliant - Closeable is retrieved from unknown location
    fis.available();

    br = new BufferedReader(new FileReader("")); // Compliant - only look at local variables

    BufferedWriter bw = new BufferedWriter(new FileWriter("")); // Compliant - closeable state is lost
    Object o = getObject(bw);

    closeable = new FileReader(""); // Compliant - responsability of closing the variable is not in the method

    RandomAccessFile raf = new RandomAccessFile("", "r"); // Compliant - Closeable is returned so its state is unknown
    return (Closeable) raf;
  }

  void closeable_not_closed_in_loops() throws Exception {
    Reader reader = null;
    for (int i = 0; i < MAX_LOOP; i++) {
      reader = new FileReader(""); // Noncompliant - false negative
    }
    reader.close();

    int i = 0;
    InputStream is = null;
    while (i < MAX_LOOP) {
      is = new FileInputStream(""); // Noncompliant - false negative
      i++;
    }
    is.close();

    Writer writer = null;
    for (int j = 0; j < MAX_LOOP; j++) {
      writer = new FileWriter(""); // Compliant
      writer.close();
    }
  }

  void closeable_not_closed_in_every_paths_when_using_if(boolean test, int x) throws Exception {
    Reader reader = new FileReader(""); // Noncompliant
    if (test) {
      reader.close();
    }

    reader = new FileReader(""); // Noncompliant
    if (test) {
    } else {
      reader.close();
    }

    Writer writer = new FileWriter(""); // Noncompliant - one branch is missing
    if (test) {
      writer.close();
    } else {
      if (x > 0) {
        Formatter formatter = new Formatter(); // Noncompliant - only used in the local then clause and not closed
        formatter.out();
        writer.close();
      }
    }

    BufferedWriter bw; // Compliant - ignored
    if (test) {
      bw = new BufferedWriter(new FileWriter("")); // IGNORED - use another closeable as argument
    } else {
      bw = new BufferedWriter(new FileWriter(""));
    }

    FileInputStream fis; // Noncompliant - not closed in else branch
    if (test) {
      fis = new FileInputStream("");
      fis.close();
    } else {
      fis = new FileInputStream("");
    }

    FileInputStream fis1; // Compliant - has an unknown status
    if (test) {
      fis1 = new FileInputStream("");
    } else {
      fis1 = getFileInputStream();
    }

    FileInputStream fis2; // Compliant - has an unknown status
    if (test) {
      fis2 = getFileInputStream();
    } else {
      fis2 = new FileInputStream("");
    }

    FileInputStream fis3; // Noncompliant - not closed after the if
    if (test) {
      fis3 = new FileInputStream("");
    } else {
    }

    FileInputStream fis4; // Nonompliant - not closed after the if
    if (test) {
    } else {
      fis4 = new FileInputStream("");
    }

    InputStream is = new FileInputStream(""); // Compliant
    if (test) {
      is.close();
    } else {
      is.close();
      if (x > 0) {
        Formatter formatter = new Formatter(); // Compliant
        formatter.close();
      }
    }
  }

  void closeable_not_closed_in_every_paths_when_using_switch(MyEnum enumValue) throws Exception {

    Formatter formatter = new Formatter(); // Noncompliant - false negative
    switch (enumValue) {
      case A:
        formatter.close();
        break;
      case B:
        formatter.flush();
        break;
      default:
        formatter.close();
        break;
    }

    OutputStream os = new FileOutputStream(""); // Compliant - (unknown) as Closeable instance is later used as method parameter
    switch (enumValue) {
      case A:
        myMethod(os);
        break;
      default:
        os.close();
        break;
    }
  }

  int closeable_used_in_try_with_resource() throws Exception {
    InputStream is = new FileInputStream(""); // Noncompliant
    try {
      is.close();
    } catch (IOException e) {

    } catch (Exception e) {
      is.close();
    }
    
    Reader reader; // Compliant
    try {
      reader = new FileReader("");
    } catch (Exception e) {
      reader = new FileReader("");
    }
    reader.close();

    Writer writer; // Compliant
    try {
      writer = new FileWriter("");
    } catch (Exception e) {

    } finally {
      writer = new FileWriter("");
    }
    writer.close();
    
    FileWriter fileWriter = new FileWriter(""); // Compliant
    try {
      fileWriter.flush();
    } catch (Exception e) {

    } finally {
      fileWriter.close();
    }
    
    try (BufferedReader br = new BufferedReader(new FileReader(""))) { // Compliant
      // ...
    } catch (Exception e) {
      // ...
    }
    
    try {
      FileInputStream fis = new FileInputStream(""); // Compliant
      try {
      } finally {
        fis.close();
      }
    } catch (Exception E) {
    }
  }

  void myMethod(Closeable closeable) {
  }

  FileInputStream getFileInputStream() throws Exception {
    return new FileInputStream("");
  }

  Object getObject(Closeable closeable) {
    return new Object();
  }
}

abstract class B {
  static final int MAGIC_NUMBER = 42;

  abstract void foo();

  void bar(Closeable closeable) throws Exception {
    int a = MAGIC_NUMBER;
    int b;
    b = MAGIC_NUMBER;
    InputStream[] streams = new InputStream[5];
    streams[0] = new FileInputStream("");
    streams[0].close();
    getInputStream().close();
    closeable.close();
    bar(closeable);
    gul(MAGIC_NUMBER);
  }

  void gul(int number) {
  }

  InputStream getInputStream() {
    return null;
  }
}

class MyCloseable implements Closeable {
  void foo() {
    close();
  }

  public void close() {
  }
}