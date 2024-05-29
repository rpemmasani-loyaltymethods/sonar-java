package checks;

class NestedTernaryOperatorsCheckSample {

  void qfTest1(int i, int j){

    // fix@qf1 {{Extract this nested ternary operation into an independent statement.}}
    // edit@qf1 [[sl=+0;el=+0;sc=5;ec=59]] {{String s;\n    if (i == j) {\n      s = "equal";\n    } else if (i < j) {\n      s = "less";\n    } else {\n      s = "more";\n    }}}
    // Noncompliant@+1 {{Extract this nested ternary operation into an independent statement.}} [[sl=+1;el=+1;sc=16;ec=58;quickfixes=qf1]]
    String s = i == j ? "equal" : i < j ? "less" : "more";
  }

  void qfTest2(int i, int j){

    // fix@qf2 {{Extract this nested ternary operation into an independent statement.}}
    // edit@qf2 [[sl=+0;el=+0;sc=5;ec=105]] {{String s;\n    if (i == j) {\n      if (i >= 0) {\n        s = "equal, nonnegative";\n      } else {\n        s = "equal, negative";\n      }\n    } else if (i < j) {\n      s = "less";\n    } else {\n      s = "more";\n    }}}
    // Noncompliant@+1 {{Extract this nested ternary operation into an independent statement.}} [[sl=+1;el=+1;sc=16;ec=105;quickfixes=qf2]]
    String s = i == j ? (i >= 0 ? "equal, nonnegative" : "equal, negative") : (i < j ? "less" : "more");
  }

  // fix@qf3 {{Extract this nested ternary operation into an independent statement.}}
  // edit@qf3 [[sl=+0;el=+14;sc=5;ec=27]] {{if (i < -10) {\n      if (j < i) {\n        s = "j < i < -10";\n      } else if (j == i) {\n        s = "j = i < -10";\n      } else if (j < -10) {\n        s = "i < j < -10";\n      } else {\n        s = "i < -10 <= j";\n      }\n    } else if (i == -10) {\n      if (i < j) {\n        s = "-10 = i < j";\n      } else if (i == j) {\n        s = "i = j = -10";\n      } else {\n        s = "j < i = -10";\n      }\n    } else {\n      s = "something else";\n    }}}
  void qfTest3(int i, int j, String s){
    // Noncompliant@+1 {{Extract this nested ternary operation into an independent statement.}} [[sl=+1;el=+15;sc=9;ec=27;quickfixes=qf3]]
    s = (i < -10) ?
          (j < i) ?
            "j < i < -10"
            : (j == i) ?
              "j = i < -10"
              : (j < -10) ?
                "i < j < -10"
                : "i < -10 <= j"
      : (i == -10) ?
          (i < j) ?
            "-10 = i < j"
            : (i == j) ?
              "i = j = -10"
              : "j < i = -10"
        : "something else";
  }

}
