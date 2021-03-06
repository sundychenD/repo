package teammates.ui.controller;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;

import teammates.common.datatransfer.CourseAttributes;
import teammates.common.datatransfer.FeedbackSessionAttributes;
import teammates.common.exception.EntityDoesNotExistException;
import teammates.common.util.ActivityLogEntry;
import teammates.common.util.Config;
import teammates.common.util.Const;
import teammates.common.util.StatusMessage;
import teammates.common.util.TimeHelper;
import teammates.common.util.Const.StatusMessageColor;
import teammates.logic.api.GateKeeper;
import teammates.logic.api.Logic;

import com.google.appengine.api.log.AppLogLine;
import com.google.appengine.api.log.LogQuery;
import com.google.appengine.api.log.LogServiceFactory;
import com.google.appengine.api.log.RequestLogs;
import com.google.appengine.api.log.LogService.LogLevel;

public class AdminActivityLogPageAction extends Action {
    
    //We want to pull out the application logs
    private boolean includeAppLogs = true;
    private static final int RELEVANT_LOGS_PER_PAGE = 50;
    private static final int SEARCH_TIME_INCREMENT = 2*60*60*1000;  // two hours in millisecond
    private static final int MAX_SEARCH_TIMES = 12;                 // maximum 1 day
    
    private int totalLogsSearched;
    private boolean isFirstRow = true;
    @Override
    protected ActionResult execute() throws EntityDoesNotExistException{
        
        new GateKeeper().verifyAdminPrivileges(account);
        
        AdminActivityLogPageData data = new AdminActivityLogPageData(account);
        
        String searchTimeOffset = getRequestParamValue("searchTimeOffset");
        if (searchTimeOffset == null) {
            searchTimeOffset = "";
        }
        String filterQuery = getRequestParamValue("filterQuery");
        String courseIdFromSearchPage = getRequestParamValue("courseId");
        
        String logRoleFromAjax = getRequestParamValue("logRole");
        String logGoogleIdFromAjax = getRequestParamValue("logGoogleId");
        String logTimeInAdminTimeZoneFromAjax = getRequestParamValue("logTimeInAdminTimeZone");
        
        boolean isLoadingLocalTimeAjax = (logRoleFromAjax != null)
                                         && (logGoogleIdFromAjax != null)
                                         && (logTimeInAdminTimeZoneFromAjax != null);
        
        if (isLoadingLocalTimeAjax) {
            data.setLogLocalTime(getLocalTimeInfo(logGoogleIdFromAjax, 
                                                  logRoleFromAjax,
                                                  logTimeInAdminTimeZoneFromAjax));
            return createAjaxResult(data);
        }
        
//      This parameter determines whether the logs with requests contained in "excludedLogRequestURIs" in AdminActivityLogPageData
//      should be shown. Use "?all=true" in URL to show all logs. This will keep showing all
//      logs despite any action or change in the page unless the the page is reloaded with "?all=false" 
//      or simply reloaded with this parameter omitted.
        boolean ifShowAll = getRequestParamAsBoolean("all");
        
        
//      This determines whether the logs related to testing data should be shown. Use "testdata=true" in URL
//      to show all testing logs. This will keep showing all logs from testing data despite any action or change in the page
//      unless the the page is reloaded with "?testdata=false"  or simply reloaded with this parameter omitted.       
        boolean ifShowTestData = getRequestParamAsBoolean("testdata");
        
        if (filterQuery == null) {
            filterQuery = "";
        }
        //This is used to parse the filterQuery. If the query is not parsed, the filter function would ignore the query
        data.generateQueryParameters(filterQuery);
        
        List<ActivityLogEntry> logs = null;
        if (data.isFromDateSpecifiedInQuery()) {
            logs = searchLogsWithExactTimePeriod(data);
        } else {
            if (!searchTimeOffset.isEmpty()) {
                data.setToDate(Long.parseLong(searchTimeOffset));
            }
            logs = searchLogsWithTimeIncrement(data);
        }
        generateStatusMessage(data, logs, courseIdFromSearchPage);
        data.init(ifShowAll, ifShowTestData, logs);
        
        if (searchTimeOffset.isEmpty()) {
            return createShowPageResult(Const.ViewURIs.ADMIN_ACTIVITY_LOG, data);
        }
        
        return createAjaxResult(data);
    }
    
    private void generateStatusMessage(AdminActivityLogPageData data, List<ActivityLogEntry> logs, String courseId) {
        String status = "Total Logs gone through in last search: " + totalLogsSearched + "<br>";
        status += "Total Relevant Logs found in last search: " + logs.size() + "<br>";
        
        long earliestSearchTime = data.getFromDate();
        ActivityLogEntry earliestLogChecked = null;
        if (!logs.isEmpty()) {
            earliestLogChecked = logs.get(logs.size() - 1);
        }
        //  if the search space is limited to a certain log
        if ((logs.size() >= RELEVANT_LOGS_PER_PAGE) && (earliestLogChecked != null)) {
            earliestSearchTime = earliestLogChecked.getTime();
        }
        
        double targetTimeZone = Const.DOUBLE_UNINITIALIZED;
        if (data.isPersonSpecified()) {
            String targetUserGoogleId = data.getPersonSpecified();
            targetTimeZone = getLocalTimeZoneForRequest(targetUserGoogleId, "");

            if (targetTimeZone == Const.DOUBLE_UNINITIALIZED) {
                // if the user is unregistered, try finding the timezone by course id passed from Search page
                if ((courseId != null) && (!courseId.isEmpty())) {
                    targetTimeZone = getLocalTimeZoneForUnregisteredUserRequest(courseId);
                }
            }
        } else {
            targetTimeZone = Const.SystemParams.ADMIN_TIMZE_ZONE_DOUBLE;
        }
        
        double adminTimeZone = Const.SystemParams.ADMIN_TIMZE_ZONE_DOUBLE;
        String timeInAdminTimeZone = computeLocalTime(adminTimeZone, String.valueOf(earliestSearchTime));
        String timeInUserTimeZone =  computeLocalTime(targetTimeZone, String.valueOf(earliestSearchTime));
        status += "The earliest log entry checked on <b>" + timeInAdminTimeZone + "</b> in Admin Time Zone (" 
                  + adminTimeZone + ") and ";
        if (targetTimeZone != Const.DOUBLE_UNINITIALIZED) {
            status += "on <b>" + timeInUserTimeZone + "</b> in Local Time Zone (" + targetTimeZone + ").<br>";
        } else {
            status += timeInUserTimeZone + ".<br>";
        }
        
        // the "Search More" button to continue searching from the previous fromDate 
        status += "<button class=\"btn-link\" id=\"button_older\" onclick=\"submitFormAjax(" + (data.getFromDate() + 1) + ");\">Search More</button>";
        
        status += "<input id=\"ifShowAll\" type=\"hidden\" value=\""+ data.getIfShowAll() +"\"/>";
        status += "<input id=\"ifShowTestData\" type=\"hidden\" value=\""+ data.getIfShowTestData() +"\"/>";
        
        data.setStatusForAjax(status);
        statusToUser.add(new StatusMessage(status, StatusMessageColor.INFO));
    }

    private List<ActivityLogEntry> searchLogsWithTimeIncrement(AdminActivityLogPageData data) {
        List<ActivityLogEntry> appLogs = new LinkedList<ActivityLogEntry>();
        
        int numberOfSearchTimes = 0;
        totalLogsSearched = 0;
        while ((numberOfSearchTimes < MAX_SEARCH_TIMES) && (appLogs.size() < RELEVANT_LOGS_PER_PAGE)) {
            // set fromDate is two-hour away from toDate
            long nextTwoHour = data.getToDate() - SEARCH_TIME_INCREMENT;
            data.setFromDate(nextTwoHour);
            numberOfSearchTimes++;
            
            LogQuery query = buildQuery(data);
            List<ActivityLogEntry> searchResult = searchLogsByQuery(query, data);
            if (!searchResult.isEmpty()) {
                appLogs.addAll(searchResult);
            }
            data.setToDate(data.getFromDate() + 1);
        }
        return appLogs;
    }
    
    private List<ActivityLogEntry> searchLogsWithExactTimePeriod(AdminActivityLogPageData data) {
        totalLogsSearched = 0;
        LogQuery query = buildQuery(data);
        List<ActivityLogEntry> appLogs = searchLogsByQuery(query, data);
        return appLogs;
    }

    private List<ActivityLogEntry> searchLogsByQuery(LogQuery query, AdminActivityLogPageData data) {
        List<ActivityLogEntry> appLogs = new LinkedList<ActivityLogEntry>();
        //fetch request log
        Iterable<RequestLogs> records = LogServiceFactory.getLogService().fetch(query);
        for (RequestLogs record : records) {
            //fetch application log
            List<AppLogLine> appLogLines = record.getAppLogLines();
            for (AppLogLine appLog : appLogLines) {
                totalLogsSearched++;
                String logMsg = appLog.getLogMessage();
                if (logMsg.contains("TEAMMATESLOG") && !logMsg.contains("adminActivityLogPage")) {
                    ActivityLogEntry activityLogEntry = new ActivityLogEntry(appLog);                   
                    activityLogEntry = data.filterLogs(activityLogEntry);
                    if (activityLogEntry.toShow() && ((!activityLogEntry.isTestingData()) || data.getIfShowTestData())) {
                        if (isFirstRow ) {
                            activityLogEntry.setFirstRow();
                            isFirstRow = false;
                        }
                        appLogs.add(activityLogEntry);
                    }
                }
            }    
        }
        return appLogs;
    }
    
    private LogQuery buildQuery(AdminActivityLogPageData data) {
        LogQuery query = LogQuery.Builder.withDefaults();
        List<String> versions = data.getVersions();
        
        query.includeAppLogs(includeAppLogs);
        query.batchSize(1000);
        query.minLogLevel(LogLevel.INFO);
        query.startTimeMillis(data.getFromDate());
        query.endTimeMillis(data.getToDate());
        
        try {
            query.majorVersionIds(getVersionIdsForQuery(versions));
        } catch (Exception e) {
            isError = true;
            statusToUser.add(new StatusMessage(e.getMessage(), StatusMessageColor.DANGER));
        }
        
        return query;
    }
    
    private List<String> getVersionIdsForQuery(List<String> versions) {
        
        boolean isVersionSpecifiedInRequest = (versions != null && !versions.isEmpty());
        if (isVersionSpecifiedInRequest) {   
            return versions;        
        }       
        return getDefaultVersionIdsForQuery();
    }
    
    private List<String> getDefaultVersionIdsForQuery() {
    
        String currentVersion = Config.inst().getAppVersion();
        List<String> defaultVersions = new ArrayList<String>();
        
        //Check whether version Id contains alphabet 
        //Eg. 5.05rc
        if (currentVersion.matches(".*[A-z.*]")) {
            //if current version contains alphatet,
            //by default just prepare current version as a single element for the query
            defaultVersions.add(currentVersion.replace(".", "-"));
            
        } else {
            //current version does not contain alphabet
            //by default prepare current version with preceding 3 versions
            defaultVersions = getRecentVersionIdsWithDigitOnly(currentVersion);
        }
        
        return defaultVersions;        
    }
    
    private List<String> getRecentVersionIdsWithDigitOnly(String currentVersion) {
        
        List<String> recentVersions = new ArrayList<String>();
        
        double curVersionAsDouble = Double.parseDouble(currentVersion);
        recentVersions.add(currentVersion.replace(".", "-"));
        
        //go back for three preceding versions
        //subtract from double form of current version id
        //Eg. current version is 4.01 --> 4.00, 3.99, 3.98  --> 4-00, 3-99, 3-98
        for (int i = 1; i < 4; i++) {

            double preVersionAsDouble = curVersionAsDouble - 0.01 * i;
            if (preVersionAsDouble > 0) {
                String preVersion = String.format("%.2f", preVersionAsDouble)
                                          .replace(".", "-");
                
                recentVersions.add(preVersion);
            }
        }
        
        return recentVersions;
    }
    
    /*
     * Functions used to load local time for activity log using AJAX
     */
    
    private double getLocalTimeZoneForRequest(String userGoogleId, String userRole) {
        double localTimeZone = Const.DOUBLE_UNINITIALIZED;
        
        if ((userRole != null) && (userRole.contentEquals("Admin") || userRole.contains("(M)"))) {
            return Const.SystemParams.ADMIN_TIMZE_ZONE_DOUBLE;
        }
        
        Logic logic = new Logic();
        if (userGoogleId != null && !userGoogleId.isEmpty()) {     
            try {
                localTimeZone = findAvailableTimeZoneFromCourses(logic.getCoursesForInstructor(userGoogleId));
            } catch (EntityDoesNotExistException e) {
                localTimeZone = Const.DOUBLE_UNINITIALIZED;
            }
            
            if (localTimeZone != Const.DOUBLE_UNINITIALIZED) {
                return localTimeZone;
            }
             
            try {
                localTimeZone = findAvailableTimeZoneFromCourses(logic.getCoursesForStudentAccount(userGoogleId));
            } catch (EntityDoesNotExistException e) {
                localTimeZone = Const.DOUBLE_UNINITIALIZED;
            }
            
            if (localTimeZone != Const.DOUBLE_UNINITIALIZED) {
                return localTimeZone;
            }
        }
        
        return localTimeZone;
    }
    
    private double findAvailableTimeZoneFromCourses(List<CourseAttributes> courses) {
        
        double localTimeZone = Const.DOUBLE_UNINITIALIZED;
        
        if (courses == null) {
            return localTimeZone;
        }
        
        Logic logic = new Logic();
        
        for (CourseAttributes course : courses) {
            List<FeedbackSessionAttributes> fsl = logic.getFeedbackSessionsForCourse(course.id); 
            if (fsl != null && !fsl.isEmpty()) {
                return fsl.get(0).timeZone;
            }
        }
        
        return localTimeZone;
    }
    
    private double getLocalTimeZoneForUnregisteredUserRequest(String courseId) {
        double localTimeZone = Const.DOUBLE_UNINITIALIZED;
        
        if (courseId == null || courseId.isEmpty()) {
            return localTimeZone;
        }
        
        Logic logic = new Logic();
        
        List<FeedbackSessionAttributes> fsl = logic.getFeedbackSessionsForCourse(courseId); 
        if (fsl != null && !fsl.isEmpty()) {
            return fsl.get(0).timeZone;
        }
        
        return localTimeZone;
        
    }
    
    private double getLocalTimeZoneInfo(String logGoogleId, String logRole) {
        if (!logGoogleId.contentEquals("Unknown") && !logGoogleId.contentEquals("Unregistered")) {
            return getLocalTimeZoneForRequest(logGoogleId, logRole);
        } else if (logRole.contains("Unregistered") && !logRole.contentEquals("Unregistered")) {
            String coureseId = logRole.split(":")[1];
            return getLocalTimeZoneForUnregisteredUserRequest(coureseId);
        } else {
            return Const.DOUBLE_UNINITIALIZED;
        }
    }
    
    private String getLocalTimeInfo(String logGoogleId, String logRole, String logTimeInAdminTimeZone) {
        double timeZone = getLocalTimeZoneInfo(logGoogleId, logRole);
        if (timeZone != Const.DOUBLE_UNINITIALIZED) {
            return computeLocalTime(timeZone, logTimeInAdminTimeZone);
        } else {
            return "Local Time Unavailable";
        }
    }
    
    private String computeLocalTime(double timeZone, String logTimeInAdminTimeZone) {
        if (timeZone == Const.DOUBLE_UNINITIALIZED) {
            return "Local Time Unavailable";
        }
        
        Calendar appCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        appCal.setTimeInMillis(Long.parseLong(logTimeInAdminTimeZone));
        TimeHelper.convertToUserTimeZone(appCal, timeZone);
        return sdf.format(appCal.getTime());
    }
}
