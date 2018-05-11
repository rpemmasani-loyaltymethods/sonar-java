import javax.naming.directory.SearchControls;
import java.util.Date;
import java.util.Properties;

class S4434 {

  void nonCompliantCreate(int scope, long countLimit, int timeLimit, String[] attributes, boolean deref) {
    SearchControls ctrl = new SearchControls(scope, countLimit, timeLimit, attributes, true, deref); // Noncompliant
  }

  void nonCompliantSet() {
    SearchControls ctrl = new SearchControls();
    ctrl.setReturningObjFlag(true); // Noncompliant
  }

  void compliantCreate(int scope, long countLimit, int timeLimit, String[] attributes, boolean deref) {
    new SearchControls(scope, countLimit, timeLimit, attributes, false, deref);
  }

  void compliantCreate(int scope, long countLimit, int timeLimit, String[] attributes, boolean retobj, boolean deref) {
    new SearchControls(scope, countLimit, timeLimit, attributes, retobj, deref);
  }

  void compliantSet() {
    SearchControls ctrl = new SearchControls();
    ctrl.setReturningObjFlag(false);
  }

  void compliantSet(boolean returnObject) {
    SearchControls ctrl = new SearchControls();
    ctrl.setReturningObjFlag(returnObject);
  }
}
