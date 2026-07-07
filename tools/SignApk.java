import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Minimal apksigner replacement built on the apksig library.
 * Usage: SignApk <keystore.p12> <storepass> <alias> <in.apk> <out.apk>
 */
public class SignApk {
    public static void main(String[] args) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(args[0])) {
            ks.load(in, args[1].toCharArray());
        }
        PrivateKey key = (PrivateKey) ks.getKey(args[2], args[1].toCharArray());
        List<X509Certificate> certs = new ArrayList<>();
        for (Certificate c : ks.getCertificateChain(args[2])) {
            certs.add((X509Certificate) c);
        }

        ApkSigner.SignerConfig signer =
                new ApkSigner.SignerConfig.Builder("debug", key, certs).build();
        new ApkSigner.Builder(Collections.singletonList(signer))
                .setInputApk(new File(args[3]))
                .setOutputApk(new File(args[4]))
                // v1/JAR signing needs sun.security PKCS7 APIs removed in modern
                // JDKs; v2 alone is valid for minSdk >= 24 installs.
                .setV1SigningEnabled(false)
                .setV2SigningEnabled(true)
                .build()
                .sign();

        ApkVerifier.Result result = new ApkVerifier.Builder(new File(args[4])).build().verify();
        if (!result.isVerified()) {
            System.err.println("Signature verification FAILED");
            System.exit(1);
        }
        System.out.println("Signed and verified: v1=" + result.isVerifiedUsingV1Scheme()
                + " v2=" + result.isVerifiedUsingV2Scheme());
    }
}
