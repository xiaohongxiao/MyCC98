package tk.djcrazy.libCC98;

import android.app.Application;
import android.graphics.Bitmap;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.StringRequest;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.orhanobut.logger.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.UnknownServiceException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ch.boye.httpclientandroidlib.cookie.Cookie;
import ch.boye.httpclientandroidlib.entity.mime.MultipartEntity;
import ch.boye.httpclientandroidlib.entity.mime.content.FileBody;
import ch.boye.httpclientandroidlib.entity.mime.content.StringBody;
import ch.boye.httpclientandroidlib.impl.cookie.BasicClientCookie;
import tk.djcrazy.MyCC98.R;
import tk.djcrazy.MyCC98.application.MyApplication;
import tk.djcrazy.MyCC98.config.Config;
import tk.djcrazy.libCC98.data.BoardEntity;
import tk.djcrazy.libCC98.data.BoardStatus;
import tk.djcrazy.libCC98.data.HotTopicEntity;
import tk.djcrazy.libCC98.data.InboxInfo;
import tk.djcrazy.libCC98.data.LoginType;
import tk.djcrazy.libCC98.data.PostContentEntity;
import tk.djcrazy.libCC98.data.PostEntity;
import tk.djcrazy.libCC98.data.SearchResultEntity;
import tk.djcrazy.libCC98.data.UserData;
import tk.djcrazy.libCC98.data.UserProfileEntity;
import tk.djcrazy.libCC98.exception.NoUserFoundException;
import tk.djcrazy.libCC98.util.ParamMapBuilder;
import tk.djcrazy.libCC98.util.RequestResultListener;

/**
 * Created by DJ on 13-7-28.
 */
@Singleton
public class NewCC98Service {
    public static final String TAG = "NewCC98Service";



    @Inject
    private ICC98UrlManager mUrlManager;
    @Inject
    private Application mApplication;
    @Inject
    private NewCC98Parser mCC98Parser;

    public void submitPmInfoRequest(Object tag, int type, int page, final RequestResultListener<InboxInfo> listener) {
        String url = type==0? mUrlManager.getInboxUrl(page):mUrlManager.getOutboxUrl(page);
        Request request = new StringRequest(url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    InboxInfo info = mCC98Parser.parsePmList(response);
                    listener.onRequestComplete(info);
                } catch (Exception e) {
                    e.printStackTrace();
                    listener.onRequestError(e.getMessage());
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
                listener.onRequestError(error.getMessage());
            }
        });
        request.setTag(tag);
        getApplication().mRequestQueue.add(request);
    }

    public void submitUpdateRequest(Object tag, final RequestResultListener<JSONObject> listener) {
        final String updateLink=getApplication().getApplicationContext().getString(R.string.application_update_link);
        Request request = new StringRequest(Request.Method.GET, updateLink,new Response.Listener<String>() {
            @Override
            public void onResponse(String result) {
                try {
                    listener.onRequestComplete(new JSONObject(result));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        },new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                //listener.onRequestError("更新遇到问题");
                Logger.e("更新遇到问题 "+updateLink);
            }
        });
        request.setTag(tag);
        getApplication().mRequestQueue.add(request);
    }

    public void login(final Object tag, final String userName, final String pwd32,  final String pwd16, String proxyName,
                      String proxyPwd, String proxyHost, LoginType type ,final RequestResultListener<Boolean> listener) {
        Log.d(this.getClass().getSimpleName(), userName+pwd32+proxyName+proxyPwd+proxyHost+type);
        final UserData userData = new UserData();
        userData.setProxyUserName(proxyName);
        userData.setProxyHost(proxyHost);
        userData.setProxyPassword(proxyPwd);
        userData.setLoginType(type);
        userData.setUserName(userName);
        userData.setPassword16(pwd16);
        userData.setPassword32(pwd32);
        StringRequest request = new StringRequest(Request.Method.POST, mUrlManager.getLoginUrl(type, proxyHost),new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                if (response.contains("9898")){
                    getUserAvatar1(tag, userData,listener);
                 } else {
                    listener.onRequestError("用户名或密码错误");
                    getApplication().syncUserDataAndHttpClient();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                listener.onRequestError(error.getMessage());
                getApplication().syncUserDataAndHttpClient();
            }
        }) {
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                return  new ParamMapBuilder()
                        .param("a", "i").param("u", userName).param("p", pwd32).param("userhidden", "2").buildMap();
            }
        };
        getApplication().syncUserDataAndHttpClient(userData);
        request.setTag(tag);
        getApplication().mRequestQueue.add(request);
    }


    private void getUserAvatar1(final Object tag, final UserData userData, final RequestResultListener<Boolean> listener) {
        Request request = new StringRequest(mUrlManager.getUserProfileUrl(
                userData.getLoginType(),userData.getProxyHost(), userData.getUserName()), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    userData.setCookies(castToAnother(getApplication().mHttpClient.getCookieStore().getCookies()));
                    String avatarLink =  mCC98Parser.parseUserAvatar(response, userData.getLoginType(), userData.getProxyHost());
                     getUserAvatar2(tag, userData, avatarLink, listener);
                } catch (Exception e) {
                    getApplication().syncUserDataAndHttpClient();
                    listener.onRequestError("解析头像地址失败，请重试");
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                listener.onRequestError("获取头像地址失败，请重试");
            }
        });
        request.setTag(tag);
        getApplication().mRequestQueue.add(request);
    }

    private void getUserAvatar2(final Object tag, final UserData userData, String url, final RequestResultListener<Boolean> listener){
        Request request = new ImageRequest(url, new Response.Listener<Bitmap>() {
            @Override
            public void onResponse(Bitmap response) {
                getApplication().addNewUser(userData, response, true);
                listener.onRequestComplete(true);
            }
        }, 200, 200, Bitmap.Config.ARGB_8888, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d(this.getClass().getSimpleName(),"download image failed", error);
                getApplication().syncUserDataAndHttpClient();
                listener.onRequestError("下载头像失败，请重试");
            }
        });
        request.setTag(tag);
        getApplication().mRequestQueue.add(request);
    }

    public void submitBitmapRequest(final Object tag, String url, final RequestResultListener<Bitmap> listener) {
        Request request = new ImageRequest(url, new Response.Listener<Bitmap>() {
            @Override
            public void onResponse(Bitmap response) {
                listener.onRequestComplete(response);
            }
        }, 0, 0, Bitmap.Config.ARGB_8888, new Response.ErrorListener(){
            @Override
            public void onErrorResponse(VolleyError error) {
                listener.onRequestError("图片加载失败");
            }
        });
        request.setTag(tag);
        getApplication().mRequestQueue.add(request);
    }

    public void submitPostContentRequest(final Object tag, String boardId, String postId, int pageNum,
                                         boolean aForceRefresh, final RequestResultListener<List<PostContentEntity>> listener) {
        Request request = new StringRequest(mUrlManager.getPostUrl(boardId,postId,pageNum),new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    List<PostContentEntity> result = mCC98Parser.parsePostContentList(response);
                    listener.onRequestComplete(result);
                } catch (Exception e) {
                    Logger.t(TAG).e(e, "post error");//e.printStackTrace();
                    listener.onRequestError(e.getMessage());
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Logger.t(TAG).e(error, "post error");//error.printStackTrace();
                listener.onRequestError(error.getMessage());
            }
        });
        request.setTag(tag);
        getApplication().mRequestQueue.add(request);
    }


    public void submitGetMsgContent(final Object tag, int pmId, final RequestResultListener<String> listener) {
        Request request = new StringRequest(mUrlManager.getMessagePageUrl(pmId), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    String res = mCC98Parser.parseMsgContent(response);
                    listener.onRequestComplete(res);
                } catch (Exception e) {
                    listener.onRequestError(e.getMessage());
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                listener.onRequestError(error.getMessage());
            }
        });
        request.setTag(tag);
        getApplication().mRequestQueue.add(request);
    }
    public void submitAddFriendRequest(final Object tag, final String userName, final RequestResultListener<Boolean> listener) {
        Request request = new StringRequest(Request.Method.POST, mUrlManager.getAddFriendUrl(), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                if (response.contains("好友添加成功")) {
                    listener.onRequestComplete(true);
                } else if (response.contains("论坛没有这个用户，操作未成功")) {
                    listener.onRequestError("论坛没有这个用户");
                } else {
                    listener.onRequestError("未知错误");
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                listener.onRequestError(error.getMessage());
            }
        }){
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                return new ParamMapBuilder().param("todo", "saveF").param("touser", userName)
                        .param("Submit", "保存").buildMap();
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return new ParamMapBuilder().param("Referer", mUrlManager.getAddFriendUrlReferrer()).buildMap();
            }
        };
        request.setTag(tag);
        getApplication().mRequestQueue.add(request);
    }

    public void submitUserProfileRequest(final Object tag, String userName, final RequestResultListener<UserProfileEntity> listener) {
        Request request = new StringRequest(mUrlManager.getUserProfileUrl(userName), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    listener.onRequestComplete(mCC98Parser.parseUserProfile(response));
                } catch (Exception e) {
                    listener.onRequestError(e.getMessage());
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                listener.onRequestError(error.getMessage());
            }
        });
        request.setTag(tag);
        getApplication().mRequestQueue.add(request);
    }


    public void submitUploadFileRequest(final Object tag, final File file, final RequestResultListener<String> listener) {
        Request request = new StringRequest(Request.Method.POST, mUrlManager.getUploadPictureUrl(),new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    Logger.i(response);
                    String res = mCC98Parser.parseUploadPicture(response);
                    listener.onRequestComplete(res);
                } catch (Exception e) {
                    e.printStackTrace();
                    listener.onRequestError(e.getMessage());
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
                listener.onRequestError(error.getMessage());
            }
        }){
            @Override
            public byte[] getBody() throws AuthFailureError {
                try {
                    MultipartEntity reqEntity = new MultipartEntity();
                    reqEntity.addPart("act", new StringBody("upload"));
                    reqEntity.addPart("fname", new StringBody(file.getName()));
                    reqEntity.addPart("file1", new FileBody(file));
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    reqEntity.writeTo(bos);
                    return bos.toByteArray();
                } catch (Exception e) {
                    Log.e(NewCC98Service.class.getSimpleName(), "",e);
                    return super.getBody();
                }
            }
        };
        request.setTag(tag);
        getApplication().mRequestQueue.add(request);
    }


    public void submitPersonalBoardList(final Object tag, final RequestResultListener<List<BoardEntity>> listener) {
        Request request = new StringRequest(mUrlManager.getPersonalBoardUrl(),new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    List<BoardEntity> list = mCC98Parser.parsePersonalBoardList(response);
                    listener.onRequestComplete(list);
                } catch (Exception e) {
                    listener.onRequestError(e.getMessage());
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                listener.onRequestError(error.getMessage());
            }
        });
        request.setTag(tag);
        getApplication().mRequestQueue.add(request);
    }

    public void submitHotTopicList(final Object tag, final RequestResultListener<List<HotTopicEntity>> listener) {
        Request request = new StringRequest(mUrlManager.getHotTopicUrl(),new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    List<HotTopicEntity> list = mCC98Parser.parseHotTopicList(response);
                    listener.onRequestComplete(list);
                } catch (Exception e) {
                    listener.onRequestError(e.getMessage());
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                listener.onRequestError(error.getMessage());
            }
        });
        request.setTag(tag);
        getApplication().mRequestQueue.add(request);
    }
    public void submitNewTopicList(final Object tag,int pageNumber, final RequestResultListener<List<SearchResultEntity>> listener) {
        Request request = new StringRequest(mUrlManager.getNewPostUrl(pageNumber),new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    List<SearchResultEntity> list = mCC98Parser.parseQueryResult(response);
                    listener.onRequestComplete(list);
                } catch (Exception e) {
                    Log.e(NewCC98Service.class.getSimpleName(), "submitNewTopicList failed", e);
                    listener.onRequestError(e.getMessage());
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(NewCC98Service.class.getSimpleName(), "submitNewTopicList failed", error.getCause());
                listener.onRequestError(error.getMessage());
            }
        });
        request.setTag(tag);
        getApplication().mRequestQueue.add(request);
    }

    public void submitBoardList(final Object tag, final String boardId, final RequestResultListener<List<BoardEntity>> listener) {
        Request request = new StringRequest(mUrlManager.getBoardUrl(boardId),new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    List<BoardEntity> list = mCC98Parser.parseBoardList(response);
                    listener.onRequestComplete(list);
                } catch (Exception e) {
                    listener.onRequestError(e.getMessage());
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                listener.onRequestError(error.getMessage());
            }
        });
        request.setTag(tag);
        getApplication().mRequestQueue.add(request);
    }

    public void submitPostList(final Object tag, final String boardId, int pageNumber, final RequestResultListener<List<PostEntity>> listener) {
        Request request = new StringRequest(mUrlManager.getBoardUrl(boardId, pageNumber),new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    List<PostEntity> list = mCC98Parser.parsePostList(response);
                    listener.onRequestComplete(list);
                } catch (Exception e) {
                    listener.onRequestError(e.getMessage());
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                listener.onRequestError(error.getMessage());
            }
        });
        request.setTag(tag);
        getApplication().mRequestQueue.add(request);
    }

    public void submitPostSearch(final Object tag, String keyword, String boardId, String type, int pageNumber, final RequestResultListener<List<SearchResultEntity>> listener) {
        Request request = new StringRequest(mUrlManager.getSearchUrl(keyword, boardId, type, pageNumber),new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    List<SearchResultEntity> list = mCC98Parser.parseQueryResult(response);
                    listener.onRequestComplete(list);
                } catch (Exception e) {
                    listener.onRequestError(e.getMessage());
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                listener.onRequestError(error.getMessage());
            }
        });
        request.setTag(tag);
        getApplication().mRequestQueue.add(request);
    }

    public void submitTodayBoardlList(final Object tag, final RequestResultListener<List<BoardStatus>> listener) {
        Request request = new StringRequest(mUrlManager.getTodayBoardList(), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    List<BoardStatus> list = mCC98Parser.parseTodayBoardList(response);
                    listener.onRequestComplete(list);
                } catch (Exception e) {
                    listener.onRequestError("版面列表加载失败");
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                listener.onRequestError("版面列表加载失败");
            }
        });
        request.setTag(tag);
        getApplication().mRequestQueue.add(request);
    }


    private List<BasicClientCookie> castToAnother(List<Cookie> list) {
        List<BasicClientCookie> res = new ArrayList<BasicClientCookie>();
        for (Cookie cookie: list) {
            res.add((BasicClientCookie)cookie);
        }
        return res;
    }

    public void cancelRequest(Object object) {
        getApplication().mRequestQueue.cancelAll(object);
    }

    public ImageLoader getImageLoader() {
        return getApplication().mImageLoader;
    }

    public Bitmap getCurrentUserAvatar(){
        return getApplication().getCurrentUserAvatar();
    }

    public  UserData getCurrentUserData() {
        return  getApplication().getCurrentUserData();
    }

    public String getDomain() {
        return mUrlManager.getClientUrl();
    }

    private MyApplication getApplication() {
        return (MyApplication) mApplication;
    }
}
