package teammates.test.util;

import teammates.test.driver.TestProperties;

public class Url extends teammates.common.util.Url {

    public Url(String url) {
        super(url);
    }
    
    /**
     * Returns the absolute version of the URL by appending the application's URL
     * as defined in test.properties to the URL input if the URL input not yet
     * absolute, or just the URL input otherwise.
     */
    @Override
    public String toAbsoluteString() {
        return urlString.startsWith(TestProperties.inst().TEAMMATES_URL) 
                                        ? urlString : TestProperties.inst().TEAMMATES_URL + urlString;
    }
    
}
