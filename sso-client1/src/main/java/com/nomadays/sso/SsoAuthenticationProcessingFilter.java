package com.nomadays.sso;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.session.ExpiringSession;
import org.springframework.session.SessionRepository;

/**
 * Filter to ensure the authentication.
 * Registers on /login_callback url, and checks the returned token from Login server.
 * if the Session is valid and exists, it simply sets the 'SESSION' cookie same as the login server & returns the authentication.
 * 
 * Must configure it to replace UsernamePasswordAuthenticationFilter, since we don't need form login.
 * 
 * @author beku
 * 
 * @deprecated use {@link SsoClientLoginCallbackFilter} instead.
 *
 */
@Deprecated
public class SsoAuthenticationProcessingFilter extends AbstractAuthenticationProcessingFilter {
	
	private Logger logger = LoggerFactory.getLogger(getClass());
	private SessionRepository<ExpiringSession> sessionRepository;
	private RequestCache requestCache;
	
	public final String TOKEN_PARAM = "token";

	@SuppressWarnings("unchecked")
	public <S extends ExpiringSession> SsoAuthenticationProcessingFilter(SessionRepository<S> sessionRepository, RequestCache requestCache) {
		super(new AntPathRequestMatcher("/login_callback"));
		this.sessionRepository = (SessionRepository<ExpiringSession>) sessionRepository;
		this.requestCache = requestCache;
	}

	@Override
	public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
			throws AuthenticationException, IOException, ServletException {
		String token = request.getParameter(TOKEN_PARAM);
		if(token != null){
			String sessionId = decodeAndDecrypt(token);
			ExpiringSession session = sessionRepository.getSession(sessionId);
			if(session != null){
				logger.debug("found valid session {}", session);
				SecurityContext securityContext = session.getAttribute("SPRING_SECURITY_CONTEXT");
				
				Long expiryInMilliSeconds = new Long(session.getMaxInactiveIntervalInSeconds()) * 1000 + session.getCreationTime();
				Long maxAge = (expiryInMilliSeconds - new Date().getTime())/1000;
				Cookie cookie = new Cookie("SESSION", sessionId);
				cookie.setMaxAge(Integer.parseInt(maxAge.toString()));
				response.addCookie(cookie);
				
				SavedRequest savedRequest = requestCache.getRequest(request, response);
				if (savedRequest != null) {
					logger.debug("re-enforcing cached request at {} {}", savedRequest.getRedirectUrl(), savedRequest.getMethod());
					// inspired from HttpSessionRequestCache
					session.setAttribute("SPRING_SECURITY_SAVED_REQUEST", savedRequest);
					sessionRepository.save(session);
				} 
				return securityContext.getAuthentication();
			}
		}
		 
		return null;
	}
	
	private String decodeAndDecrypt(String token){
		try {
			// decode
			byte[] decoded = Base64.getUrlDecoder().decode(token);
			
			String key = "G~Y@86-FtH&gq'_e"; // 128 bit key, better be handled in external properties
			// Create key and cipher
			Key aesKey = new SecretKeySpec(key.getBytes(), "AES");
			Cipher cipher = Cipher.getInstance("AES");
			// Decrypt
			cipher.init(Cipher.DECRYPT_MODE, aesKey);
            String decrypted = new String(cipher.doFinal(decoded));
            return decrypted;
		
		} catch (IllegalBlockSizeException | BadPaddingException | NoSuchAlgorithmException
				| NoSuchPaddingException | InvalidKeyException e) {
			
		} 
		return null;
	}

}
