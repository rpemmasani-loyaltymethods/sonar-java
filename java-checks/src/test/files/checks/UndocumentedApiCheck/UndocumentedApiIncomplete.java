package org.foo;

public class Empty {} // Noncompliant {{Document this public class by adding an explicit description.}} - no javadoc

/**
 * FIXME
 */
public class A { } // Noncompliant {{Document this public class by adding an explicit description.}} - placeholder

/**
 * .
 */
public class B { } // Noncompliant {{Document this public class by adding an explicit description.}} - placeholder

/**
 * ...
 */
public class C { } // Noncompliant {{Document this public class by adding an explicit description.}} - placeholder

/**
 * TODO
 */
public class D { } // Noncompliant {{Document this public class by adding an explicit description.}} - placeholder

/**
 *
 */
public class E { } // Noncompliant {{Document this public class by adding an explicit description.}} - empty javadoc

/**
 * OneWordIsNotEnough
 */
public class F { } // Noncompliant {{Document this public class by adding an explicit description.}} - more than one word is required

/**
 * {@inheritDoc}
 */
public class G extends F { } // Compliant

/**
 * This is documented
 */
public class H {
  /**
   * This is documented
   */
  public void foo() { }

  /**
   * This is documented
   * @param sab
   */
  public void foo(String s, Object o) { } // Noncompliant {{Document the parameter(s): s, o}} - wrong name

  /**
   * This is documented
   * @param o
   * @param o FIXME
   */
  public void foo(Object o) { } // Noncompliant {{Document the parameter(s): o}} - undocumented

  /**
   * This is documented
   * @param l .
   */
  public void foo(long l) { } // Noncompliant {{Document the parameter(s): l}} - placeholder

  /**
   * This is documented
   * @param i the number of fools
   */
  public void foo(int i) { } // Compliant - documented parameter

  /**
   * @param d the ratio of fools
   */
  public void foo(double d) { } // Noncompliant {{Document this public method by adding an explicit description.}} - param documented but not the method

  /**
   * @return
   */
  public String bar1() { } // Noncompliant {{Document this public method by adding an explicit description.}} - undocumented

  /**
   * @return this is documented
   */
  public String bar2() { } // Complinant - non-void method with no parameter described with the return value

  /**
   * This is documented
   */
  public void bar3() { } // Complinant

  /**
   * FIXME
   */
  public void bar4() { } // Noncompliant {{Document this public method by adding an explicit description.}}

  /**
   * This is documented
   * @return FIXME
   */
  public String bro() { } // Noncompliant {{Document this method return value.}} - placeholder

  /**
   * This is documented
   * @return the mighty qix in the face
   */
  public String big() { } // Compliant - documented @return

  /**
   * This is documented
   */
  public void tiu() throws MyException, org.foo.MyOtherException { } // Noncompliant {{Document this method thrown exception(s): MyException, MyOtherException}}

  /**
   * This is documented
   * @throws
   */
  public void taa() throws MyException { } // Noncompliant {{Document this method thrown exception(s): MyException}}

  /**
   * This is documented
   * @throws BOOM
   */
  public void toi() throws MyException { } // Noncompliant {{Document this method thrown exception(s): MyException}}

  /**
   * This is documented
   * @throws MyException FIXME
   * @throws MyException when it does not like you
   */
  public void tou() throws MyException { } // Noncompliant {{Document this method thrown exception(s): MyException}}

  /**
   * This is documented
   * @throws MyException when it does not like you
   * @throws MyException FIXME
   */
  public void tul() throws MyException { } // Noncompliant {{Document this method thrown exception(s): MyException}}

  /**
   * This is documented
   * @throws MyException TODO
   * @throws MyException FIXME
   */
  public void tac() throws MyException { } // Noncompliant {{Document this method thrown exception(s): MyException}}

  /**
   * This is documented
   * @throws MyException when it does not like you
   */
  public void tya() throws MyException { } // Compliant - exception documented

  private static class MyException extends Exception { }
  private static class MyOtherException extends Exception { }
}



