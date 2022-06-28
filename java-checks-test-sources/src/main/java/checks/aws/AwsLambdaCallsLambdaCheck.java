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
import lombok.SneakyThrows;

public class AwsLambdaCallsLambdaCheck {

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

      awsLambda.invoke(invokeRequest);
    }

    @Override
    public Void handleRequest(Object o, Context context) {
      invokeRequest = new InvokeRequest()
        .withFunctionName(MY_FUNCTION);

      awsLambda = AWSLambdaClientBuilder.standard()
        .withCredentials(new ProfileCredentialsProvider())
        .withRegion(Regions.US_WEST_2).build();

      awsLambda.invoke(invokeRequest); // Noncompliant

      invokeAsync();

      transitiveCallee1();

      return null;
    }

    void invokeAsync(){
      invokeRequest = new InvokeRequest();
      // Makes call async
      invokeRequest.setInvocationType("Event");
      invokeRequest.withFunctionName(MY_FUNCTION);

      // Compliant as call is async
      awsLambda.invoke(invokeRequest);
    }

    // Not the correct 'handleRequest' signature
    public void handleRequest(String o, Context context) {
      awsLambda.invoke(invokeRequest); // compliant
      return null;
    }

    void transitiveCallee1() {
      transitiveCallee2();
    }

    void transitiveCallee2() {
      transitiveCallee3();
    }

    void transitiveCallee3() {
      awsLambda.invoke(invokeRequest); // Noncompliant
    }
  }

  static class RequestStreamHandlerImpl implements RequestStreamHandler {
    private InvokeRequest invokeRequest = null;
    private AWSLambda awsLambda = null;

    public RequestStreamHandlerImpl() throws SQLException {
      invokeRequest = new InvokeRequest()
        .withFunctionName(MY_FUNCTION);

      awsLambda = AWSLambdaClientBuilder.standard()
        .withCredentials(new ProfileCredentialsProvider())
        .withRegion(Regions.US_WEST_2).build();

      awsLambda.invoke(invokeRequest);
    }

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
      awsLambda.invoke(invokeRequest); // Noncompliant
    }
  }
}
