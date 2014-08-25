package com.lr.androidemailpgp.crypto;

import org.spongycastle.openpgp.PGPCompressedData;
import org.spongycastle.openpgp.PGPEncryptedDataList;
import org.spongycastle.openpgp.PGPException;
import org.spongycastle.openpgp.PGPLiteralData;
import org.spongycastle.openpgp.PGPObjectFactory;
import org.spongycastle.openpgp.PGPOnePassSignature;
import org.spongycastle.openpgp.PGPOnePassSignatureList;
import org.spongycastle.openpgp.PGPPrivateKey;
import org.spongycastle.openpgp.PGPPublicKey;
import org.spongycastle.openpgp.PGPPublicKeyEncryptedData;
import org.spongycastle.openpgp.PGPSecretKey;
import org.spongycastle.openpgp.PGPSecretKeyRingCollection;
import org.spongycastle.openpgp.PGPSignatureList;
import org.spongycastle.openpgp.PGPUtil;
import org.spongycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.spongycastle.openpgp.operator.jcajce.JcePublicKeyDataDecryptorFactoryBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.SignatureException;
import java.util.Iterator;

public class Decryptor {

    private static PGPPrivateKey findSecretKey(String privateKeyString, long keyID,
                                               char[] pass) throws IOException, PGPException,
            NoSuchProviderException {
        PGPSecretKeyRingCollection pgpSec = new PGPSecretKeyRingCollection(
                PGPUtil.getDecoderStream(new ByteArrayInputStream(privateKeyString.getBytes())));
        PGPSecretKey pgpSecKey = pgpSec.getSecretKey(keyID);
        if (pgpSecKey == null) {
            return null;
        }
        return pgpSecKey
                .extractPrivateKey(new JcePBESecretKeyDecryptorBuilder()
                        .setProvider("SC").build(pass));
    }

	/*
	 * public PGPPrivateKey findPrivateKey(long keyID) throws PGPException,
	 * NoSuchProviderException { PGPSecretKey pgpSecKey =
	 * getPrivateKeys().getSecretKey(keyID);
	 *
	 * if (pgpSecKey == null) return null;
	 *
	 * return pgpSecKey.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder()
	 * .setProvider("SC").build(passphrase.toCharArray())); }
	 */

    /** End Accessor Methods **/

    /**
     * Decryption Instance Methods
     * @throws WrongPrivateKeyException
     **/

    public static String decryptString(String encryptedString, String privateKeyString, String passPhrase) throws IOException,
            PGPException, NoSuchProviderException, SignatureException,
            VerificationFailedException, WrongPrivateKeyException {
        InputStream stream = new ByteArrayInputStream(encryptedString.getBytes());
        return new String(decryptStream(stream, privateKeyString, passPhrase));
    }

    public static byte[] decryptStream(InputStream encryptedStream, String privateKeyString, String passPhrase)
            throws IOException, PGPException, NoSuchProviderException,
            SignatureException, VerificationFailedException, WrongPrivateKeyException {

        //Security.addProvider(new BouncyCastleProvider());
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);

        InputStream decoderStream = PGPUtil.getDecoderStream(encryptedStream);

        PGPObjectFactory pgpF = new PGPObjectFactory(decoderStream);
        PGPEncryptedDataList encryptedData = null;
        Object encryptedObj = pgpF.nextObject();
        Iterator encryptedDataIterator;
        PGPPublicKeyEncryptedData publicKeyData = null;
        PGPPrivateKey privateKey = null;
        InputStream decryptedDataStream;
        PGPObjectFactory pgpFactory;
        PGPCompressedData compressedData;
        PGPLiteralData literallyTheRealFuckingData;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] returnBytes;

        // the first object might be a PGP marker packet.
        if (encryptedObj instanceof PGPEncryptedDataList)
            encryptedData = (PGPEncryptedDataList) encryptedObj;
        else
            encryptedData = (PGPEncryptedDataList) pgpF.nextObject();

        encryptedDataIterator = encryptedData.getEncryptedDataObjects();

        while (privateKey == null && encryptedDataIterator.hasNext()) {
            publicKeyData = (PGPPublicKeyEncryptedData) encryptedDataIterator
                    .next();

            privateKey = findSecretKey(privateKeyString, publicKeyData.getKeyID(), passPhrase.toCharArray());
        }

        if (privateKey == null)
            throw new WrongPrivateKeyException(
                    "secret key for message not found.");

        decryptedDataStream = publicKeyData
                .getDataStream(new JcePublicKeyDataDecryptorFactoryBuilder()
                        .setProvider("SC").build(privateKey));

        pgpFactory = new PGPObjectFactory(decryptedDataStream);

        compressedData = (PGPCompressedData) pgpFactory.nextObject();

        pgpFactory = new PGPObjectFactory(compressedData.getDataStream());

        PGPOnePassSignatureList opsList = null;
        PGPOnePassSignature ops = null;
        PGPPublicKey signingKey = null;
        Object obj = pgpFactory.nextObject();
        if (obj instanceof PGPOnePassSignatureList) {
            opsList = (PGPOnePassSignatureList) obj;
            ops = opsList.get(0);
			/*
			if (_publicKeys != null) {
				signingKey = _publicKeys.getPublicKey(ops.getKeyID());
				// TODO warn on key not found
			}
			// TODO warn on no public keys set
			if (signingKey != null) {
				ops.init(new JcaPGPContentVerifierBuilderProvider()
						.setProvider("SC"), signingKey);
			}
			*/

            literallyTheRealFuckingData = (PGPLiteralData) pgpFactory
                    .nextObject();
        } else if (obj instanceof PGPLiteralData) {
            literallyTheRealFuckingData = (PGPLiteralData) obj;
        } else {
            throw new RuntimeException("unexpected object");
        }

        decryptedDataStream = literallyTheRealFuckingData.getInputStream();

        int ch;
        while ((ch = decryptedDataStream.read()) >= 0) {
            if (signingKey != null) {
                ops.update((byte) ch);
            }
            outputStream.write(ch);
        }

        returnBytes = outputStream.toByteArray();
        outputStream.close();

        if (signingKey != null) {
            PGPSignatureList sigList = (PGPSignatureList) pgpFactory
                    .nextObject();
            if (!ops.verify(sigList.get(0))) {
                throw new VerificationFailedException(
                        "Error: Signature could not be verified.");
            }
        }

        return returnBytes;
    }

}
