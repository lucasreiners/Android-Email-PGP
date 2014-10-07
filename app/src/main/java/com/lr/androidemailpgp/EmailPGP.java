package com.lr.androidemailpgp;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.lr.androidemailpgp.crypto.Decryptor;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class EmailPGP implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    private XSharedPreferences keysPref;
    private AlertDialog passPhraseDialog = null;
    private ArrayList<String> passphrases;

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        keysPref = new XSharedPreferences("com.lr.androidemailpgp", "keys");
        keysPref.makeWorldReadable();
        XposedBridge.log("Keyspref length: " + keysPref.getAll().entrySet().size());
        passphrases = new ArrayList<String>();
    }



    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        if(loadPackageParam.packageName.equals("com.android.email")){

            final Class<?> messageClass = XposedHelpers.findClass("com.android.mail.providers.Message", loadPackageParam.classLoader);
            final Class<?> conversationMessageClass = XposedHelpers.findClass("com.android.mail.browse.ConversationMessage", loadPackageParam.classLoader);
            final Class<?> secureConversationViewControllerClass = XposedHelpers.findClass("com.android.mail.ui.SecureConversationViewController", loadPackageParam.classLoader);
            final Class<?> messageHeaderViewClass = XposedHelpers.findClass("com.android.mail.browse.MessageHeaderView", loadPackageParam.classLoader);
            final Class<?> secureConversationViewControllerCallbacksClass = XposedHelpers.findClass("com.android.mail.ui.SecureConversationViewControllerCallbacks", loadPackageParam.classLoader);
            final Class<?> composeActivityClass = XposedHelpers.findClass("com.android.mail.compose.ComposeActivity", loadPackageParam.classLoader);

            XposedHelpers.findAndHookMethod(secureConversationViewControllerClass, "renderMessage", "com.android.mail.browse.ConversationMessage", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {

                    final String bodyText = (String) XposedHelpers.findField(conversationMessageClass, "bodyText").get(param.args[0]);
                    String mTo = (String) XposedHelpers.findField(conversationMessageClass, "mTo").get(param.args[0]);

                    Object mCallBacksFieldObject = XposedHelpers.findField(secureConversationViewControllerClass, "mCallbacks").get(param.thisObject);
                    final Fragment containerFragment = (Fragment) XposedHelpers.callMethod(mCallBacksFieldObject, "getFragment");
                    final Context emailPGPContext = ((Context) containerFragment.getActivity()).createPackageContext("com.lr.androidemailpgp", Context.CONTEXT_IGNORE_SECURITY);

                    if (bodyText != null && bodyText.contains("-----BEGIN PGP MESSAGE-----")) {

                        String[] toAdresses = (String[]) XposedHelpers.callStaticMethod(messageClass, "tokenizeAddresses", mTo);
                        for(int i = 0; i< toAdresses.length; i++){
                            toAdresses[i] = toAdresses[i].substring(toAdresses[i].indexOf("<") + 1, toAdresses[i].indexOf(">"));
                            if(keysPref.contains("key_private_" + toAdresses[i])){
                                XposedBridge.log("Private key found for " + toAdresses[i]);

                                final String privateKey = keysPref.getString("key_private_" + toAdresses[i], "");

                                //Iterate through all the passphraes to find one already entered

                                for(int k = 0; k < passphrases.size(); k++){
                                    try {
                                        String plainText = Decryptor.decryptString(bodyText, privateKey, passphrases.get(k));
                                        XposedHelpers.findField(conversationMessageClass, "bodyText").set(param.args[0], plainText);
                                        XposedHelpers.callMethod(param.thisObject, "renderMessage", param.args[0]);

                                        TextView statusTextView = new TextView(containerFragment.getActivity());
                                        statusTextView.setText(emailPGPContext.getString(R.string.message_decryption_success));
                                        statusTextView.setPadding(16, 16, 16, 16);
                                        statusTextView.setBackgroundColor(Color.parseColor("#259b24"));
                                        statusTextView.setTextColor(Color.WHITE);
                                        statusTextView.setGravity(Gravity.CENTER);
                                        statusTextView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                                        Object messageHeaderViewObject = XposedHelpers.findField(secureConversationViewControllerClass, "mMessageHeaderView").get(param.thisObject);
                                        XposedHelpers.callMethod(messageHeaderViewObject, "addView", statusTextView);

                                        return;
                                    } catch (Exception ex){

                                    }
                                }

                                //If this point is reached, we need to open the new passphrase dialog


                                if (passPhraseDialog == null || (passPhraseDialog != null && !passPhraseDialog.isShowing())) {
                                    AlertDialog.Builder passPhraseDialogBuilder = new AlertDialog.Builder(containerFragment.getActivity());
                                    passPhraseDialogBuilder.setTitle(emailPGPContext.getResources().getString(R.string.dialog_passphrase_title));
                                    LinearLayout dialogLayout = new LinearLayout(containerFragment.getActivity());
                                    dialogLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                                    //Stolen from: http://stackoverflow.com/questions/4275797/view-setpadding-accepts-only-in-px-is-there-anyway-to-setpadding-in-dp
                                    int padding_in_dp = 16;
                                    final float scale = containerFragment.getActivity().getResources().getDisplayMetrics().density;
                                    int padding_in_px = (int) (padding_in_dp * scale + 0.5f);

                                    dialogLayout.setPadding(padding_in_px,padding_in_px,padding_in_px,padding_in_px);
                                    dialogLayout.setOrientation(LinearLayout.VERTICAL);

                                    TextView messageTextView = new TextView(containerFragment.getActivity());
                                    messageTextView.setText(emailPGPContext.getResources().getText(R.string.dialog_passphrase_body));
                                    messageTextView.setTypeface(messageTextView.getTypeface(), Typeface.BOLD);
                                    dialogLayout.addView(messageTextView);

                                    final EditText passPhraseEditText = new EditText(containerFragment.getActivity());
                                    passPhraseEditText.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                                    passPhraseEditText.setHint(emailPGPContext.getResources().getString(R.string.dialog_passphrase_title));
                                    passPhraseEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                                    dialogLayout.addView(passPhraseEditText);

                                    passPhraseDialogBuilder.setView(dialogLayout);
                                    passPhraseDialogBuilder.setPositiveButton(emailPGPContext.getString(R.string.decrypt), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            passphrases.add(passPhraseEditText.getText().toString());
                                            try {
                                                String plainText = Decryptor.decryptString(bodyText, privateKey, passPhraseEditText.getText().toString());
                                                XposedHelpers.findField(conversationMessageClass, "bodyText").set(param.args[0], plainText);
                                                XposedHelpers.callMethod(param.thisObject, "renderMessage", param.args[0]);

                                                TextView statusTextView = new TextView(containerFragment.getActivity());
                                                statusTextView.setText(emailPGPContext.getString(R.string.message_decryption_success));
                                                statusTextView.setPadding(16, 16, 16, 16);
                                                statusTextView.setBackgroundColor(Color.parseColor("#259b24"));
                                                statusTextView.setTextColor(Color.WHITE);
                                                statusTextView.setGravity(Gravity.CENTER);
                                                statusTextView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                                                Object messageHeaderViewObject = XposedHelpers.findField(secureConversationViewControllerClass, "mMessageHeaderView").get(param.thisObject);
                                                XposedHelpers.callMethod(messageHeaderViewObject, "addView", statusTextView);

                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    });

                                    passPhraseDialogBuilder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            Object conversationMessageObject = param.args[0];
                                            try {

                                                TextView statusTextView = new TextView(containerFragment.getActivity());
                                                statusTextView.setText(emailPGPContext.getString(R.string.message_decryption_error_passphrase));
                                                statusTextView.setPadding(16, 16, 16, 16);
                                                statusTextView.setBackgroundColor(Color.parseColor("#e51c23"));
                                                statusTextView.setTextColor(Color.WHITE);
                                                statusTextView.setGravity(Gravity.CENTER);
                                                statusTextView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                                                Object messageHeaderViewObject = XposedHelpers.findField(secureConversationViewControllerClass, "mMessageHeaderView").get(param.thisObject);
                                                XposedHelpers.callMethod(messageHeaderViewObject, "addView", statusTextView);


                                            } catch (IllegalAccessException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    });

                                    passPhraseDialog = passPhraseDialogBuilder.create();
                                    passPhraseDialog.show();

                                    return;
                                }

                            } else{
                                XposedBridge.log("No private key found for " + toAdresses[i]);

                                TextView statusTextView = new TextView(containerFragment.getActivity());
                                statusTextView.setText(emailPGPContext.getString(R.string.message_decryption_error_privatekey));
                                statusTextView.setPadding(16, 16, 16, 16);
                                statusTextView.setBackgroundColor(Color.parseColor("#e51c23"));
                                statusTextView.setTextColor(Color.WHITE);
                                statusTextView.setGravity(Gravity.CENTER);
                                statusTextView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                                Object messageHeaderViewObject = XposedHelpers.findField(secureConversationViewControllerClass, "mMessageHeaderView").get(param.thisObject);
                                XposedHelpers.callMethod(messageHeaderViewObject, "addView", statusTextView);

                            }
                        }
                    }
                }
            });
            /*
            XposedHelpers.findAndHookMethod(composeActivityClass, "doSend", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    EditText mBodyView = (EditText) XposedHelpers.findField(composeActivityClass, "mBodyView").get(param.thisObject);
                    XposedBridge.log("ComposeText = " + mBodyView.getText().toString());
                }
            });
            */
        }
    }

    /** {@code XposedHelpers.callMethod()} cannot call methods of the super class of an object, because it
     * uses {@code getDeclaredMethods()}. So we have to implement this little helper, which should work
     * similar to {@code }callMethod()}. Furthermore, the exceptions from getMethod() are passed on.
     * <p>
     * At the moment, only argument-free methods supported (only case needed here). After a discussion
     * with the Xposed author it looks as if the functionality to call super methods will be implemented
     * in {@code XposedHelpers.callMethod()} in a future release.
     *
     * @param obj Object whose method should be called
     * @param methodName String representing the name of the argument-free method to be called
     * @return The object that the method call returns
     * @see <a href="http://forum.xda-developers.com/showpost.php?p=42598280&postcount=1753">
     *     Discussion about calls to super methods in Xposed's XDA thread</a>
     */
    private Object callSuperMethod(Object obj, String methodName) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return obj.getClass().getMethod(methodName).invoke(obj);
    }
}