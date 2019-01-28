package com.xiaoji.duan.auo;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.codec.binary.Base64;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.TemplateHandler;
import io.vertx.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;

public class MainVerticle extends AbstractVerticle {

	private ThymeleafTemplateEngine thymeleaf = null;
	private MongoClient mongodb = null;

	@Override
	public void start(Future<Void> startFuture) throws Exception {
		JsonObject config = new JsonObject();
		config.put("host", config().getString("mongo.host", "duan-mongo"));
		config.put("port", config().getInteger("mongo.port", 27017));
		config.put("keepAlive", config().getBoolean("mongo.keepalive", true));
		mongodb = MongoClient.createShared(vertx, config);

		initDefaultApps();
		
		thymeleaf = ThymeleafTemplateEngine.create(vertx);
		TemplateHandler templatehandler = TemplateHandler.create(thymeleaf);

		ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
		resolver.setSuffix(".html");
		resolver.setCacheable(false);
		resolver.setTemplateMode("HTML5");
		resolver.setCharacterEncoding("utf-8");
		thymeleaf.getThymeleafTemplateEngine().setTemplateResolver(resolver);

		Router router = Router.router(vertx);

		StaticHandler staticfiles = StaticHandler.create().setCachingEnabled(false).setWebRoot("static");
		router.route("/auo/static/*").handler(staticfiles);
		router.route("/auo").pathRegex("\\/.+\\.json").handler(staticfiles);

		BodyHandler datahandler = BodyHandler.create();
		router.route("/auo").pathRegex("\\/*").handler(datahandler);
		
		router.route("/auo/dologin").handler(datahandler);
		router.route("/auo/dologin").produces("application/json").handler(ctx -> this.doLogin(ctx));

		router.route("/auo/doregister").handler(datahandler);
		router.route("/auo/doregister").produces("application/json").handler(ctx -> this.doRegister(ctx));

		router.route("/auo/api/*").handler(datahandler);
		router.route("/auo/api/access_token").handler(ctx -> this.accessToken(ctx));
		router.route("/auo/api/refresh_token").handler(ctx -> this.refreshToken(ctx));
		router.route("/auo/api/userinfo").handler(ctx -> this.userinfo(ctx));

		router.route("/auo/register").handler(ctx -> this.register(ctx));
		router.route("/auo/login").handler(ctx -> this.login(ctx));
		
		router.route("/auo").pathRegex("\\/[^\\.]*").handler(templatehandler);

		HttpServerOptions option = new HttpServerOptions();
		option.setCompressionSupported(true);

		vertx.createHttpServer(option).requestHandler(router::accept).listen(8080, http -> {
			if (http.succeeded()) {
				startFuture.complete();
				System.out.println("HTTP server started on http://localhost:8080");
			} else {
				startFuture.fail(http.cause());
			}
		});
	}

	private void register(RoutingContext ctx) {
		ctx.next();
	}
	
	private void doRegister(RoutingContext ctx) {
		JsonObject data = ctx.getBodyAsJson();

		System.out.println(data.encode());
		
		mongodb.findOne("auo_user_info",
				new JsonObject().put("openid", data.getString("useremail")),
				new JsonObject(),
				findOne -> {
					if (findOne.succeeded()) {
						JsonObject userinfo = findOne.result() == null ? new JsonObject() : findOne.result();
						
						if (userinfo.isEmpty()) {
							userinfo
									.put("_id", Base64.encodeBase64URLSafeString(UUID.randomUUID().toString().getBytes()))
									.put("openid", data.getString("useremail"))
									.put("nickname", data.getString("username"))
									.put("password", data.getString("userpassword"))
									.put("unionid", data.getString("useremail"))
									.put("sex", "0")
									.put("province", "")
									.put("city", "")
									.put("country", "")
									.put("avatar", "")
									.put("privilege", new JsonArray());
							mongodb.save("auo_user_info", userinfo, save -> {
								if (save.succeeded()) {
									JsonObject res = userinfo.copy();
									res.remove("password");
									ctx.response().end(res.encode());
								} else {
									ctx.response().end("{}");
								}
							});
						} else {
							ctx.response().end("{}");
						}
					} else {
						ctx.response().end("{}");
					}
				}
		);
		
	}

	private void doLogin(RoutingContext ctx) {
		JsonObject data = ctx.getBodyAsJson();

		System.out.println(data.encode());

		mongodb.findOne("auo_user_info",
				new JsonObject()
				.put("openid", data.getString("useremail"))
				.put("password", data.getString("userpassword")),
				new JsonObject(),
				findOne -> {
					if (findOne.succeeded()) {
						JsonObject userinfo = findOne.result();
						
						if (userinfo != null && !userinfo.isEmpty()) {

							userinfo.put("_id", Base64.encodeBase64URLSafeString(UUID.randomUUID().toString().getBytes()));
							mongodb.save("auo_user_access", userinfo, insert -> {
								if (insert.succeeded()) {

									JsonObject result = new JsonObject()
											.put("code", userinfo.getString("_id"))
											.put("openid", userinfo.getString("openid"))
											.put("unionid", userinfo.getString("unionid"))
											.put("state", data.getString("state"));

									mongodb.save("auo_user_access", new JsonObject().mergeIn(data).mergeIn(userinfo).mergeIn(result), save -> {});
									System.out.println(result.encode());
									
									ctx.response().end(result.encode());
								} else {
									ctx.response().end(new JsonObject().encode());
								}
							});
							
						} else {
							ctx.response().end(new JsonObject().encode());
						}
					} else {
						ctx.response().end(new JsonObject().encode());
					}
				});
		
	}
	
	private void login(RoutingContext ctx) {
		// OAuth Check
        String appId = ctx.request().getParam("appid");
        String redirectUri = ctx.request().getParam("redirect_uri");
        String responseType = ctx.request().getParam("response_type");
        String scope = ctx.request().getParam("scope");
        String state = ctx.request().getParam("state");
        
        JsonObject oauth = new JsonObject()
        		.put("appid", appId)
        		.put("redirecturi", redirectUri)
        		.put("responsetype", responseType)
        		.put("scope", scope)
        		.put("state", state);
        
        System.out.println(oauth.encode());
        
        String site = "";
        
        try {
			site = new URL(redirectUri).getHost();
			
			if (site.contains(":")) {
				site = site.substring(0, site.indexOf(":"));
			}
		} catch (MalformedURLException e) {
			System.out.println(e.getMessage());
		}
        
        JsonObject query = new JsonObject()
        		.put("appid", appId)
        		.put("site", site);
        
        if (config().getBoolean("debug")) {
			ctx.put("oauth", oauth.mapTo(Map.class));
			ctx.next();
        } else {
        
	        mongodb.findOne("auo_oauth_apps", query, new JsonObject(), ar -> {
	        	if (ar.succeeded()) {
	        		JsonObject oauthapp = ar.result();
	        		
	        		if (oauthapp != null && !oauthapp.isEmpty()) {
	        			ctx.put("oauth", oauth.mapTo(Map.class));
	        			ctx.next();
	        		} else {
	        			ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/plain;charset=utf-8").end("非法AppId或网站请求!");
	        		}
	        	} else {
	    			ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/plain;charset=utf-8").end("非法AppId或网站请求!");
	        	}
	        });
        }
	}

	private void accessToken(RoutingContext ctx) {
		HttpServerRequest req = ctx.request();
		
        String appId = req.getParam("appid");
        String secret = req.getParam("secret");
        String code = req.getParam("code");
        String grantType = req.getParam("grant_type");
        
        mongodb.findOne("auo_oauth_apps",
        		new JsonObject()
        		.put("appid", appId)
        		.put("secret", secret), new JsonObject(), findOne -> {
        			if (findOne.succeeded()) {
        				JsonObject app = findOne.result();
        				
        				if (app == null || app.isEmpty()) {
            				ctx.response().end(new JsonObject().put("errcode", 10001).put("errmsg", "Access Token取得失败").encode());
        				} else {
        					
        					mongodb.findOne("auo_user_access",
        							new JsonObject().put("code", code),
        							new JsonObject(),
        							update -> {
        								if (update.succeeded()) {
        									JsonObject access = update.result();
        									System.out.println("Exist user login " + access.encode());
        									access
                							.put("access_token", Base64.encodeBase64URLSafeString(UUID.randomUUID().toString().getBytes()))
                							.put("refresh_token", Base64.encodeBase64URLSafeString(UUID.randomUUID().toString().getBytes()))
                							.put("access_time", System.currentTimeMillis())
                							.put("expires_in", 60 * 60 * 2);
        									
        									mongodb.save("auo_user_access", access, updateAT -> {});
        									
        									ctx.response().end(access.encode());
        								} else {
        		            				ctx.response().end(new JsonObject().put("errcode", 10001).put("errmsg", "Access Token取得失败").encode());
        								}
        							}
        					);
        				}
        			} else {
        				ctx.response().end(new JsonObject().put("errcode", 10001).put("errmsg", "Access Token取得失败").encode());
        			}
        		});
	}

	private void refreshToken(RoutingContext ctx) {
		HttpServerRequest req = ctx.request();
		
        String appId = req.getParam("appid");
        String refreshToken = req.getParam("refresh_token");
        String grantType = req.getParam("grant_type");

        System.out.println("Refresh token appId:" + appId + ", refreshToken:" + refreshToken + ", grantType:" + grantType);
        mongodb.findOne("auo_user_access",
				new JsonObject()
				.put("appid", appId)
				.put("refresh_token", refreshToken),
				new JsonObject(),
				findOne -> {
					if (findOne.succeeded()) {
						JsonObject refreshTokenUser = findOne.result();
						
						if (refreshTokenUser != null && !refreshTokenUser.isEmpty()) {
							
							Long accessTime = refreshTokenUser.getLong("access_time");
							Long expiresIn = refreshTokenUser.getLong("expires_in");
							
							if (System.currentTimeMillis() > accessTime + (expiresIn * 1000)) {
						        System.out.println("Refresh token get failed for expired.");
		        				ctx.response().end(new JsonObject().put("errcode", 10004).put("errmsg", "登录已失效,请重新登录.").encode());
							} else {
								refreshTokenUser.remove("_id");
								refreshTokenUser.remove("useremail");
								refreshTokenUser.remove("userpassword");
								refreshTokenUser.remove("code");
								refreshTokenUser.remove("password");
								
								ctx.response().end(refreshTokenUser.encode());
							}
							
						} else {
					        System.out.println("Refresh token get failed for no matched user access.");
	        				ctx.response().end(new JsonObject().put("errcode", 10003).put("errmsg", "Refresh token取得失败").encode());
						}
					} else {
				        System.out.println("Refresh token get failed for query error.");
        				ctx.response().end(new JsonObject().put("errcode", 10003).put("errmsg", "Refresh token取得失败").encode());
					}
				});        
	}

	private void userinfo(RoutingContext ctx) {
		HttpServerRequest req = ctx.request();
		
        String openId = req.getParam("openid");
        String accessToken = req.getParam("access_token");
        
		mongodb.findOne("auo_user_access",
				new JsonObject()
				.put("openid", openId)
				.put("access_token", accessToken),
				new JsonObject(),
				findOne -> {
					if (findOne.succeeded()) {
						JsonObject access = findOne.result();
						
						if (access != null && !access.isEmpty()) {
							mongodb.findOne("auo_user_info",
									new JsonObject().put("unionid", access.getString("unionid")),
									new JsonObject(),
									findUser -> {
										if (findUser.succeeded()) {
											JsonObject userinfo = findUser.result();
											
											userinfo.remove("password");
											userinfo.remove("_id");
											
											if (userinfo != null && !userinfo.isEmpty()) {
												ctx.response().end(userinfo.encode());
											} else {
						        				ctx.response().end(new JsonObject().put("errcode", 10002).put("errmsg", "userinfo取得失败").encode());
											}
										} else {
					        				ctx.response().end(new JsonObject().put("errcode", 10002).put("errmsg", "userinfo取得失败").encode());
										}
									}
							);
						} else {
	        				ctx.response().end(new JsonObject().put("errcode", 10002).put("errmsg", "userinfo取得失败").encode());
						}
					} else {
        				ctx.response().end(new JsonObject().put("errcode", 10002).put("errmsg", "userinfo取得失败").encode());
					}
				}
		);
	}
	
	private void initDefaultApps() {
		mongodb.save("auo_oauth_apps", 
				new JsonObject()
				.put("_id", Base64.encodeBase64URLSafeString(config().getString("apps.default.appid", "www.guobaa.com").getBytes()))
				.put("appid", Base64.encodeBase64URLSafeString(config().getString("apps.default.appid", "www.guobaa.com").getBytes()))
				.put("secret", Base64.encodeBase64URLSafeString(config().getString("apps.default.secret", "secret@www.guobaa.com").getBytes()))
				.put("site", config().getString("apps.default.site", "www.guobaa.com")),
				ar -> {
					if (ar.succeeded()) {
						System.out.println("Default site appid initialized.");
					} else {
						System.out.println("Default site appid initialize failed.");
					}
				}
		);
	}
}
