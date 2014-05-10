package net.crappo.android.androics;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.os.Bundle;

/* 
 * AccountAuthService.javaから呼び出されてアカウントを作るだけしかしないAuthenticatorクラス。
 * なので、addAccount以外のメソッドは空の実装である。
 */
public class AndroIcsAuthenticator extends AbstractAccountAuthenticator {
    private Context svcContext = null;

    public AndroIcsAuthenticator(Context context) {
        super(context);
        svcContext = context;
    }

    @Override
    public Bundle addAccount(AccountAuthenticatorResponse response, String accountType, String authTokenType, String[] requiredFeatures, Bundle options)
            throws NetworkErrorException {
        Bundle result = new Bundle();
        AccountManager acntMgr = AccountManager.get(svcContext);
        Account[] accounts = acntMgr.getAccountsByType(accountType);
        // 既にアカウントがあったらエラーで抜ける。
        if (null != accounts && accounts.length > 0) {
            result.putInt(AccountManager.KEY_ERROR_CODE, -1);
            result.putString(AccountManager.KEY_ERROR_MESSAGE, "Account Already Exist.");
            return result;
        }

        String userId = TopActivity.appName;
        Account account = new Account(userId, accountType);
        acntMgr.addAccountExplicitly(account, "DummyPasswd", null);
        result.putString(AccountManager.KEY_ACCOUNT_NAME, userId);
        result.putString(AccountManager.KEY_ACCOUNT_TYPE, accountType);

        return result;
    }

    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account, Bundle options)
            throws NetworkErrorException {
        return null;
    }

    @Override
    public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
        return null;
    }

    @Override
    public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options)
            throws NetworkErrorException {
        return null;
    }

    @Override
    public String getAuthTokenLabel(String authTokenType) {
        return null;
    }

    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account, String[] features)
            throws NetworkErrorException {
        return null;
    }

    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options)
            throws NetworkErrorException {
        return null;
    }

}