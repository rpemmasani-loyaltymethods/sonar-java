package checks.regex;

import java.util.regex.Pattern;

class RegexStackOverflowCheckWithHighStackConsumption {

  Pattern[] patterns = new Pattern[] {
    Pattern.compile("(a|b)*"), // Noncompliant [[sc=22;ec=28]] {{Refactor this repetition that can lead to a stack overflow for large inputs.}}
    Pattern.compile("(.|\n)*?"), // Noncompliant
    Pattern.compile("(.|\n)*?(.|\n)*"), // Noncompliant [[sc=22;ec=30;secondary=+0]]
    Pattern.compile("(.|\n)*?"), // Noncompliant
    Pattern.compile("(ab?){42,}"), // Noncompliant
    Pattern.compile("(a|hello world)*"), // Noncompliant
    Pattern.compile("(//|#|/\\*)(.|\n)*\\w{5,}"), // Noncompliant
    Pattern.compile("((x|.){42})*"), // Noncompliant
    Pattern.compile("(abc()()()()()()()|def)*"), // Noncompliant
    // FP because we don't take into account the proper number of characters consumed by \\1:
    Pattern.compile("(......)(?:\\1|abcd)*"), // Noncompliant
    Pattern.compile("(.|\n)*"), // Noncompliant
    Pattern.compile("(.|\n)*\\w{4,}"), // Noncompliant
    Pattern.compile("(.|\n)*\\w*"), // Noncompliant
    Pattern.compile("(.|\n)*\\w"), // Noncompliant
  };

}
