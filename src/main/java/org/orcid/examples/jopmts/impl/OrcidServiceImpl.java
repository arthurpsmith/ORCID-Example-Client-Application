/**
 * =============================================================================
 * Copyright (C) 2011-12 by ORCID
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * =============================================================================
 */
package org.orcid.examples.jopmts.impl;

import java.net.URI;
import java.util.Map;

import javax.xml.transform.dom.DOMSource;

import org.orcid.examples.jopmts.OrcidException;
import org.orcid.examples.jopmts.OrcidService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AccessTokenRequiredException;
import org.springframework.security.oauth2.client.OAuth2ProtectedResourceDetails;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.OAuth2SecurityContext;
import org.springframework.security.oauth2.client.OAuth2SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2SecurityContextImpl;
import org.springframework.security.oauth2.client.token.OAuth2ClientTokenServices;
import org.springframework.security.oauth2.common.exceptions.InvalidTokenException;
import org.w3c.dom.Document;

/**
 * @author Will Simpson
 */
public class OrcidServiceImpl implements OrcidService {
	private String orcidInfoURL;
	private String orcidSearchURL;
	private OAuth2RestTemplate orcidRestTemplate;
	private OAuth2ClientTokenServices tokenServices;
	
	public Document getOrcidDocument() throws OrcidException {
    	return getOrcidDocument(null);
    }
    
    public Document getOrcidDocument(String orcid) throws OrcidException {
    	AuthenticatedResource authRes = new AuthenticatedResource(orcidRestTemplate, tokenServices);
        if (orcid == null) {
        	orcid = authRes.getToken().getOrcid();
        }
        String url = String.format(orcidInfoURL, orcid);
        return authRes.fetchDocument(url);
    }
    
    public Document searchOrcid(Map<String, String> searchTerms) throws OrcidException {
    	StringBuilder query = new StringBuilder();
    	
    	for (String field: searchTerms.keySet()) {
    		if (query.length() > 0) query.append(" AND ");
    		query.append(field + ":" + searchTerms.get(field));
    	}
    	return searchOrcid(query.toString());
    }

    public Document searchOrcid(String query) throws OrcidException {
    	AuthenticatedResource authRes = new AuthenticatedResource(orcidRestTemplate, tokenServices);
    	String url = orcidSearchURL + "?q=" + query;
    	return authRes.fetchDocument(url);
    }
    
    public void setOrcidInfoURL(String infoURL) {
        this.orcidInfoURL = infoURL;
    }

    public void setOrcidSearchURL(String infoURL) {
        this.orcidSearchURL = infoURL;
    }
    
    public void setOrcidRestTemplate(OAuth2RestTemplate template) {
        this.orcidRestTemplate = template;
    }

    public void setTokenServices(OAuth2ClientTokenServices tokenServices) {
        this.tokenServices = tokenServices;
    }

    public static class AuthenticatedResource {
    	private Authentication authentication;
    	private OAuth2ProtectedResourceDetails resource;
    	private OrcidOAuth2AccessToken token;
    	private OAuth2RestTemplate orcidRestTemplate;
    	private OAuth2ClientTokenServices tokenServices;

		public AuthenticatedResource(OAuth2RestTemplate orcidRestTemplate, OAuth2ClientTokenServices tokenServices) {
			this.orcidRestTemplate = orcidRestTemplate;
			this.tokenServices = tokenServices;
	        this.authentication = SecurityContextHolder.getContext().getAuthentication();
	        this.resource = orcidRestTemplate.getResource();
	        this.token = (OrcidOAuth2AccessToken) tokenServices.getToken(authentication, resource);
	        if (token == null) {
	            // go get an access token...
	            throw new OAuth2AccessTokenRequiredException(resource);
	        }
		}
		
		public Document fetchDocument(String url) {
			System.err.println("Fetching URL: " + url);
	        try {
	            DOMSource orcidInput = orcidRestTemplate.getForObject(URI.create(url), DOMSource.class);
	            return (Document) orcidInput.getNode();
	        } catch (InvalidTokenException badToken) {
	            // we've got a bad token, probably because it's expired.
	            // clear any stored access tokens...
	            tokenServices.removeToken(authentication, resource);
	            // go get a new access token...
	            throw new OAuth2AccessTokenRequiredException(resource);
	        } finally {
	            clearAuthorizationCode(authentication, resource);
	        }
		}
		
	    private void clearAuthorizationCode(Authentication authentication, OAuth2ProtectedResourceDetails resource) {
	        OAuth2SecurityContext oauthContext = OAuth2SecurityContextHolder.getContext();
	        if (oauthContext != null) {
	            ((OAuth2SecurityContextImpl) oauthContext).setAuthorizationCode(null);
	        }
	    }

		public Authentication getAuthentication() {
			return authentication;
		}

		public OAuth2ProtectedResourceDetails getResource() {
			return resource;
		}

		public OrcidOAuth2AccessToken getToken() {
			return token;
		}
	}

}
