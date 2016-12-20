package org.sonar.custom;

// Parameters for this test should be:
//    constructor   = "org.sonar.custom.GenericResource(java.lang.String)";
//    closingMethod = "org.sonar.custom.GenericResource#closeResource(java.lang.String)";


public class GenericResource {
  
  public static void correct(String name) {
    GenericResource resource = new GenericResource(name);
    try {
      resource.use();
    } finally {
      resource.closeResource(name);
    }
  }
  
  public static void wrong(String name) {
    GenericResource resource = new GenericResource(name);  // Noncompliant [[flows=wrong]] {{Close this "GenericResource".}} flow@wrong {{GenericResource is never closed}}
    resource.use();
  }
  
  public static void wrong(int channel) {
    GenericResource resource = new GenericResource(channel);  // Compliant because not checked
    resource.use();
  }
  
  public GenericResource(String name) {
  }
  
  public GenericResource(int channel) {
    // Used to check differentiation between signature
  }
  
  public void use() {}
  
  public void closeResource(String name) {}
  
  public void closeResource(int id) {}
  
}
