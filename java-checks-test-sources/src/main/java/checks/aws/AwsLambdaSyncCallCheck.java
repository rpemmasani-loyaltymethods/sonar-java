package checks.aws;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;

public class AwsLambdaSyncCallCheck {

  public static final String MY_FUNCTION = "myFunction";

  static class RequestHandlerImpl implements RequestHandler<Object, Void> {
    private InvokeRequest invokeRequest = null;
    private AWSLambda awsLambda = null;

    public RequestHandlerImpl() throws SQLException {
      invokeRequest = new InvokeRequest()
        .withFunctionName(MY_FUNCTION);

      awsLambda = AWSLambdaClientBuilder.standard()
        .withCredentials(new ProfileCredentialsProvider())
        .withRegion(Regions.US_WEST_2).build();

      // Compliant as not on local variable
      awsLambda.invoke(invokeRequest);
    }

    @Override
    public Void handleRequest(Object o, Context context) {
      var invokeRequest = new InvokeRequest()
        .withFunctionName(MY_FUNCTION);

      awsLambda = AWSLambdaClientBuilder.standard()
        .withCredentials(new ProfileCredentialsProvider())
        .withRegion(Regions.US_WEST_2).build();

      awsLambda.invoke(invokeRequest); // Noncompliant

      invokeSync();
      invokeAsync();
      invokeDryRun();
      invokeUnknown();

      transitiveSyncCall();

      return null;
    }

    void fromField() {
      invokeRequest = new InvokeRequest();
      invokeRequest.setInvocationType("RequestResponse");
       // Compliant as invokeRequest is not a local variable
      awsLambda.invoke(invokeRequest);
    }

    void invokeSync() {
      invokeSync1();
      invokeSync2();
    }

    void invokeSync1() {
      InvokeRequest invokeRequest = new InvokeRequest().withFunctionName(MY_FUNCTION);

      InvokeRequest invokeRequest2 = new InvokeRequest().withInvocationType("Event");

      awsLambda.invoke(invokeRequest); // Noncompliant
    }

    void invokeSync2() {
      var invokeRequest = new InvokeRequest();
      // Makes call sync
      invokeRequest.setInvocationType("RequestResponse");
      awsLambda.invoke(invokeRequest); // Noncompliant

    }

    void invokeUnknown(){
      var invokeRequest = new InvokeRequest();
      invokeRequest.setInvocationType("RequestResponse");
      foo(invokeRequest);
      // Compliant as we don't know what the call to foo did to invokeRequest
      awsLambda.invoke(invokeRequest);
    }

    void invokeAsync() {
      invokeAsync1();
      invokeAsync2();
      invokeAsync3();
      invokeAsync4(new InvokeRequest());
    }

    void invokeAsync1() {
      var invokeRequest = new InvokeRequest();
      // Makes call async
      invokeRequest.setInvocationType("Event");
      invokeRequest.withFunctionName(MY_FUNCTION);

      // Compliant as call is async
      awsLambda.invoke(invokeRequest);
    }

    void invokeAsync2(){
     InvokeRequest invokeRequest = new InvokeRequest();
     invokeRequest.withFunctionName(MY_FUNCTION).withInvocationType("Event");

     // Async call
      awsLambda.invoke(invokeRequest);
    }

    void invokeAsync3(){
      InvokeRequest invokeRequest = new InvokeRequest().withFunctionName(MY_FUNCTION)
        .withInvocationType("Event");

      // Async call
      awsLambda.invoke(invokeRequest);
    }


    void invokeAsync4(InvokeRequest invokeRequest){
      // Compliant as we don't know what invokeRequest contains
      awsLambda.invoke(invokeRequest);
    }

    void invokeDryRun(){
     InvokeRequest invokeRequest = new InvokeRequest();
     invokeRequest.withInvocationType("DryRun").withFunctionName(MY_FUNCTION);

      // Compliant as call is DryRun
      awsLambda.invoke(invokeRequest);
    }

    private InvokeRequest foo(InvokeRequest invokeRequest) {
      return new InvokeRequest();
    }

    // Not the correct 'handleRequest' signature
    public Void handleRequest(String o, Context context) {
      awsLambda.invoke(invokeRequest); // compliant
      return null;
    }

    void transitiveSyncCall() {
      transitiveSynCall2();
    }

    void transitiveSynCall2() {
      transitiveSynCall3();
    }

    void transitiveSynCall3() {
      InvokeRequest invokeRequest = new InvokeRequest()
        .withFunctionName(MY_FUNCTION)
        .withInvocationType("RequestResponse");
      awsLambda.invoke(invokeRequest); // Noncompliant
    }
  }

  static class RequestStreamHandlerImpl implements RequestStreamHandler {
    private AWSLambda awsLambda = null;

    public RequestStreamHandlerImpl() throws SQLException {
      awsLambda = AWSLambdaClientBuilder.standard()
        .withCredentials(new ProfileCredentialsProvider())
        .withRegion(Regions.US_WEST_2).build();
    }

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
       InvokeRequest invokeRequest = new InvokeRequest()
        .withFunctionName(MY_FUNCTION);

      awsLambda.invoke(invokeRequest); // Noncompliant
    }
  }
}