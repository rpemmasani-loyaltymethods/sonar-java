package checks.security;


import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import org.h2.security.SHA256;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

public class CredentialsShouldNotBeHardcodedCheck {
  private static String secretString = "hunter2";
  private static byte[] secretByteArray = new byte[]{0xC,0xA,0xF,0xE};

  public static void nonCompliant(byte[] message) {
    String inputString = "s3cr37";
    byte[] key = inputString.getBytes();
    SHA256.getHMAC(secretByteArray, message); // Noncompliant
    SHA256.getHMAC(key, message);  // Noncompliant
    SHA256.getHMAC(inputString.getBytes(), message); // Noncompliant
    SHA256.getHMAC("anotherS3cr37".getBytes(), message); // Noncompliant
    SHA256.getHMAC(secretString.getBytes(), message); // Noncompliant
  }

  public static void compliantAzure(SecretClient secretClient, String secretName, byte[] message) {
    KeyVaultSecret retrievedSecret = secretClient.getSecret(secretName);
    String secret = retrievedSecret.getValue();

    byte[] key = secret.getBytes();
    SHA256.getHMAC(key, message);
  }

  public static void compliantAws(SecretsManagerClient secretsClient, String secretName, byte[] message) {
    GetSecretValueRequest valueRequest = GetSecretValueRequest.builder()
      .secretId(secretName)
      .build();

    GetSecretValueResponse valueResponse = secretsClient.getSecretValue(valueRequest);
    String secret                        = valueResponse.secretString();

    byte[] key = secret.getBytes();
    SHA256.getHMAC(key, message);
  }
}
