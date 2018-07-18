import java.util.Date;
import java.net.HttpCookie;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.NewCookie;
import org.apache.shiro.web.servlet.SimpleCookie;
import org.springframework.security.web.savedrequest.SavedCookie;
import play.mvc.Http.CookieBuilder;

class A {

  Cookie field1 = new Cookie("name", "value"); // FN
  HttpCookie field2 = new HttpCookie("name", "value"); // FN
  javax.ws.rs.core.Cookie field3 = new javax.ws.rs.core.Cookie("name", "value"); // FN
  Cookie field4;
  Cookie field5;
  HttpCookie field6;
  UnknownCookie field7;
  private static final boolean FALSE_CONSTANT = false;

  void foo(Cookie cookie) {
    int age = cookie.getMaxAge();
  }

  Cookie servletCookie(
      Cookie firstParam, // Noncompliant [[sc=14;ec=24]] {{Make sure creating this cookie without the "secure" flag is safe here.}}
      Cookie secondParam,
      Cookie thirdParam,
      boolean param) {
    firstParam.setSecure(false);
    secondParam.setSecure(true);

    field4 = new Cookie("name, value"); // FN, ignore fields
    field5.setSecure(false); // FN, ignore fields
    this.field4 = new Cookie("name", "value"); // FN ignore fields

    Cookie cookie = new Cookie("name", "value");
    cookie.setSecure(true);

    Cookie cookie2 = new Cookie("name", "value"); // Noncompliant [[sc=12;ec=19]] {{Make sure creating this cookie without the "secure" flag is safe here.}}

    Cookie cookie3 = new Cookie("name", "value"); // Noncompliant {{Make sure creating this cookie without the "secure" flag is safe here.}}
    cookie3.setSecure(false);

    Cookie cookie5 = new Cookie("name", "value");
    cookie5.setSecure(FALSE_CONSTANT); // FN

    Cookie c6 = new Cookie("name", "value");
    if (param) {
      c6.setSecure(false); // FN
    }
    else {
      c6.setSecure(true);
    }

    Cookie c7 = new Cookie("name", "value");
    boolean b = false;
    c7.setSecure(b); // FN

    Cookie c8 = new Cookie("name", "value");
    c8.setSecure(param);

    Object c9 = new Cookie("name", "value"); // Noncompliant

    Cookie c10;
    c10 = new Cookie("name", "value");
    c10.setSecure(true);

    Object c12;  // Noncompliant [[sc=12;ec=15]] {{Make sure creating this cookie without the "secure" flag is safe here.}}
    c12 = new Cookie("name", "value");

    return new Cookie("name", "value"); // Noncompliant
  }

  HttpCookie getHttpCookie() {
    HttpCookie c1 = new HttpCookie("name", "value");
    c1.setSecure(true);

    HttpCookie c2 = new HttpCookie("name", "value"); // Noncompliant

    HttpCookie c3 = new HttpCookie("name", "value"); // Noncompliant
    c3.setSecure(false);

    HttpCookie c4 = new HttpCookie("name", "value");
    c4.setSecure(FALSE_CONSTANT); // FN

    HttpCookie c5; // Noncompliant
    c5 = new HttpCookie("name", "value");
    c3.setSecure(false);

    field6 = new HttpCookie("name, value"); // FN, ignore fields

    return new HttpCookie("name", "value"); // Noncompliant
  }

  NewCookie jaxRsNewCookie(javax.ws.rs.core.Cookie cookie) {
    NewCookie c1 = new NewCookie(cookie); // Noncompliant
    NewCookie c2 = new NewCookie(cookie, "2", 3, false); // Noncompliant
    NewCookie c3 = new NewCookie(cookie, "2", 3, true);
    NewCookie c4 = new NewCookie(cookie, "2", 3, new Date(), false, true); // Noncompliant
    NewCookie c5 = new NewCookie(cookie, "2", 3, new Date(), true, false);

    NewCookie c6 = new NewCookie("1", "2"); // Noncompliant

    NewCookie c7 = new NewCookie("1", "2", "3", "4", "5", 6, false, true); // Noncompliant
    NewCookie c8 = new NewCookie("1", "2", "3", "4", "5", 6, true, true);
    NewCookie c9 = new NewCookie("1", "2", "3", "4", 5, "6", 7, new Date(), false, true);  // Noncompliant
    NewCookie c10 = new NewCookie("1", "2", "3", "4", 5, "6", 7, new Date(), true, false);

    NewCookie c11 = new NewCookie("1", "2", "3", "4", "5", 6, true);
    NewCookie c12 = new NewCookie("1", "2", "3", "4", "5", 6, false); // Noncompliant
    NewCookie c13 = new NewCookie("1", "2", "3", "4", "5", 6, false, false);  // Noncompliant
    NewCookie c14 = new NewCookie("1", "2", "3", "4", "5", 6, true, false);

    return new NewCookie(cookie); // Noncompliant
  }

  SimpleCookie apacheShiro(SimpleCookie unknownCookie) {
    SimpleCookie c1 = new SimpleCookie(unknownCookie); // Noncompliant
    SimpleCookie c2 = new SimpleCookie(); // Noncompliant
    c2.setSecure(false);
    SimpleCookie c3 = new SimpleCookie(); // Noncompliant
    SimpleCookie c4 = new SimpleCookie("name");  // Noncompliant
    SimpleCookie c5 = new SimpleCookie("name");
    c5.setSecure(true);
    return new SimpleCookie(); // Noncompliant
  }

  SavedCookie springSavedCookie(javax.servlet.http.Cookie cookie) {
    SavedCookie c1 = new SavedCookie(cookie); // Noncompliant
    SavedCookie c2 = new SavedCookie("n", "v", "c", "d", 1, "p", false, 1); // Noncompliant
    SavedCookie c3 = new SavedCookie("n", "v", "c", "d", 1, "p", true, 1);
    return new SavedCookie("n", "v", "c", "d", 1, "p", false, 1); // Noncompliant
  }

  void playFw(play.mvc.Http.Cookie.SameSite sameSite) {
    play.mvc.Http.Cookie c11 = new play.mvc.Http.Cookie("1", "2", 3, "4", "5", false, true); // Noncompliant
    play.mvc.Http.Cookie c12 = new play.mvc.Http.Cookie("1", "2", 3, "4", "5", true, false);
    play.mvc.Http.Cookie c21 = new play.mvc.Http.Cookie("1", "2", 3, "4", "5", false, false, sameSite); // Noncompliant
    play.mvc.Http.Cookie c22 = new play.mvc.Http.Cookie("1", "2", 3, "4", "5", true, false, sameSite);
    play.mvc.Http.Cookie c4;
    c4 =  new play.mvc.Http.Cookie("1", "2", 3, "4", "5", true, true);
    CookieBuilder cb1 = play.mvc.Http.Cookie.builder("1", "2"); // Noncompliant
    cb1.withSecure(false);
    CookieBuilder cb2 = play.mvc.Http.Cookie.builder("1", "2");
    cb2.withSecure(true);
    play.mvc.Http.Cookie.builder("1", "2")
        .withMaxAge(1)
        .withPath("x")
        .withDomain("x")
        .withSecure(true)
        .withSecure(false) // Noncompliant [[sc=20;ec=27]] {{Make sure creating this cookie without the "secure" flag is safe here.}}
        .withSecure(true)
        .build();
    play.mvc.Http.Cookie c5 = play.mvc.Http.Cookie.builder("theme", "blue").withSecure(true).build();
  }

  play.mvc.Http.Cookie getC5() {
    return new play.mvc.Http.Cookie("1", "2", 3, "4", "5", false, true); // Noncompliant
  }

  play.mvc.Http.Cookie getC5() {
    return new play.mvc.Http.Cookie("1", "2", 3, "4", "5", true, true);
  }

  play.mvc.Http.Cookie getC6() {
    return play.mvc.Http.Cookie.builder("theme", "blue").withSecure(false); // Noncompliant
  }
}

class B extends Cookie {
  public Cookie c;
  public void setSecure(boolean bool) { }
  void foo() {
    setSecure(false); // FN (to avoid implementation complexity)
  }
  Date d = new Date();
  void bar(boolean x) {
    setSecure(x);
  }
  void baz() {
    setSecure(true);
    return; // code coverage
  }
  Date codeCoverage(Cookie cookie) {
    A a = new A();
    a.foo(cookie);
    Date d1 = new Date();
    Date d2;
    d2 = d1;
    d2 = new Date();
    d = d1;
    d = new Date();
    new Date() = new Date();
    UnknownClass c = new UnknownClass();
    c.setSecure(true);
    return new Date();
  }
}
