package checks;

import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;

class RedundantNullabilityAnnotationsCheckSampleNoAnnotation {

  public void methodNonNullParam(@NonNull Object o) { // Compliant
    // ...
  }

  public void methodNonNullParamTyped(List<@NonNull Object> o) { // Compliant
    // ..
  }

  @NonNull // Compliant
  public Integer methodNonNullReturn(Object o) {
    return 0;
  }

  public void methodOkay(Object o) { // Compliant
    // ...
  }

  @NullMarked // Compliant
  static class InnerClass {

    public void methodOkay(Object o) { // Compliant
      // ...
    }

  }

}

@NullMarked
class RedundantNullabilityAnnotationsCheckSample {

  public void methodNonNullParam(@NonNull Object o) { // Noncompliant {{Remove redundant nullability annotation.}}
    // ...
  }

  public void methodNonNullParamTyped(List<@NonNull Object> o) { // Noncompliant {{Remove redundant nullability annotation.}}
    // ..
  }

  @NonNull // Noncompliant {{Remove redundant nullability annotation.}}
  public Integer methodNonNullReturn(Object o) {
    return 0;
  }

  public void methodOkay(Object o) { // Compliant
    // ...
  }

  @NullMarked // Noncompliant {{Remove redundant nullability annotation.}}
  static class InnerClass {

    public void methodOkay(Object o) { // Compliant
      // ...
    }

  }

}

@NullMarked
class RedundantNullabilityAnnotationsCheckSampleMix {

  public void methodNonNullParam(@jakarta.annotation.Nonnull Object o) { // Noncompliant {{Remove redundant nullability annotation.}}
    // ...
  }

  @javax.annotation.Nonnull // Noncompliant {{Remove redundant nullability annotation.}}
  public Integer methodNonNullReturn(Object o) {
    return 0;
  }

  public void methodOkay(Object o) { // Compliant
    // ...
  }

  @NullMarked // Noncompliant {{Remove redundant nullability annotation.}}
  static class InnerClass {

    public void methodOkay(Object o) { // Compliant
      // ...
    }

  }

}

@NullMarked
record RedundantNullabilityAnnotationsCheckSampleRecord(Integer id) {

  public void methodNonNullParam(@jakarta.annotation.Nonnull Object o) { // Noncompliant {{Remove redundant nullability annotation.}}
    // ...
  }

  public void methodNonNullParamTyped(List<@NonNull Object> o) { // Noncompliant {{Remove redundant nullability annotation.}}
    // ..
  }

  @javax.annotation.Nonnull // Noncompliant {{Remove redundant nullability annotation.}}
  public Integer methodNonNullReturn(Object o) {
    return 0;
  }

  public void methodOkay(Object o) { // Compliant
    // ...
  }

  @NullMarked // Noncompliant {{Remove redundant nullability annotation.}}
  static class InnerClass {

    public void methodOkay(Object o) { // Compliant
      // ...
    }

  }

}

@NullMarked
interface RedundantNullabilityAnnotationsCheckSampleInterface {

  public void methodNonNullParam(@jakarta.annotation.Nonnull Object o); // Noncompliant {{Remove redundant nullability annotation.}}

  public void methodNonNullParamTyped(List<@NonNull Object> o); // Noncompliant {{Remove redundant nullability annotation.}}

  @javax.annotation.Nonnull // Noncompliant {{Remove redundant nullability annotation.}}
  public Integer methodNonNullReturn(Object o);

  public void methodOkay(Object o);

  @org.jspecify.annotations.NonNull // Noncompliant {{Remove redundant nullability annotation.}}
  static interface InnerClass {

    public void methodOkay(Object o);

  }

  @NullMarked // Noncompliant {{Remove redundant nullability annotation.}}
  static interface InnerRecord {

    public void methodOkay(Object o);

  }

}
