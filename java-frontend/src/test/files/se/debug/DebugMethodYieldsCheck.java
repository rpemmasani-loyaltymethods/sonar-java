abstract class A {
  static Object foo1(boolean b) { // Noncompliant {{Method 'foo1' has 2 method yields.}}
    if (b) {
      return null;
    }
    return new Object();
  }

  static Object foo2(boolean a) { // Noncompliant {{Method 'foo2' has 3 method yields.}}
    if (a) {
      bar();
    }
    return new Object();
  }

  static void bar() { // Noncompliant {{Method 'bar' has 1 method yields.}}
    // do nothing
  }

  Object bar(boolean b) { // Compliant - we do not have method behaviors for overrideable methods
    if (a) {
      return null;
    }
    return new Object();
  }

  abstract Object qix(boolean b); // Compliant

  static void plop() { // Compliant - too many steps
    boolean a = true;
    a &= (b() == C);
    a &= (b() == C);
    a &= (b() == C);
    a &= (b() == C);
    a &= (b() == C);
    a &= (b() == C);
    a &= (b() == C);
    a &= (b() == C);
    a &= (b() == C);
    a &= (b() == C);
    a &= (b() == C);
    a &= (b() == C);
    a &= (b() == C);
    a &= (b() == C);

    if (a) { //BOOM : 2^n -1 states are generated (where n is the number of lines of &= assignements in the above code) -> fail fast by not even enqueuing nodes
    }
  }
}
