package com.mapmory.controller.timeline;

import java.io.IOException;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapmory.common.domain.Search;
import com.mapmory.common.domain.SessionData;
import com.mapmory.common.util.ContentFilterUtil;
import com.mapmory.common.util.RedisUtil;
import com.mapmory.common.util.TextToImage;
import com.mapmory.common.util.TimelineUtil;
import com.mapmory.services.community.service.CommunityService;
import com.mapmory.services.recommend.domain.Recommend;
import com.mapmory.services.recommend.service.RecommendService;
import com.mapmory.services.timeline.domain.Category;
import com.mapmory.services.timeline.domain.ImageTag;
import com.mapmory.services.timeline.domain.KeywordData;
import com.mapmory.services.timeline.domain.Record;
import com.mapmory.services.timeline.dto.SummaryRecordDto;
import com.mapmory.services.timeline.service.TimelineService;


@Controller("timelineController")
@RequestMapping("/timeline/*")
public class TimelineController {
	@Autowired
	@Qualifier("timelineService")
	private TimelineService timelineService;
	
	@Autowired
	@Qualifier("communityServiceImpl")
	private CommunityService communityService;
	
	@Autowired
	private TimelineUtil timelineUtil;
	
	@Autowired
	private TextToImage textToImage;
	
    @Autowired
    private RedisUtil<SessionData> redisUtil;
	
	@Value("${timeline.kakaomap.apiKey}")
	private String kakaoMapApiKey;
	
	@Value("${timeline.tmap.apiKey}")
	private String tMapApiKey;
	
	@Value("${default.time}")
	private String defaultTime;
    
    @Value("${timeline.kakaomap.restKey}")
    private String restKey;
    

    ///////추천 추가된 부분//////
	// 맨 위에 추가할 것
	@Autowired
	@Qualifier("recommendServiceImpl")
	private RecommendService recommendService;
		

    @Value("${page.Size}")
    private int pageLimit;

    //Tmap 다중경유지 30 경로 옵션 선택
    private int searchOption=2;
	
	public TimelineController(){
		System.out.println("TimelineController default Contrctor call : " + this.getClass());
	}
	
	@GetMapping("getTimelineList")
	public String getTimelineList(Model model,
			@RequestParam(value="selectDay", required = false) Date selectDay,
			@RequestParam(value="plus", required = false) Integer plus,
			HttpServletRequest request
			) throws Exception,IOException{
		String userId = redisUtil.getSession(request).getUserId();
		
		if(selectDay==null) {
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
//		LocalDate today = LocalDate.of(2024,5,29);
		selectDay=Date.valueOf(today);
		}
		if( !(plus==null) ) {
			selectDay=(Date.valueOf(selectDay.toLocalDate().plusDays(plus)));
		}
		LocalDate tomorrow = selectDay.toLocalDate();
		
		Search search = Search.builder()
				.userId(userId)
				.selectDay1(selectDay+" "+defaultTime)
				.selectDay2((Date.valueOf(tomorrow.plusDays(1)))+" "+defaultTime)
				.timecapsuleType(0)
				.build();
		
		List<Record> timelineList=timelineService.getTimelineList(search);
		if(!(timelineList==null || timelineList.isEmpty())) {
			List<Map<String,Object>> mapList=new ArrayList<Map<String,Object>>();
			for(Record r: timelineList) {
				Map<String,Object> map=new HashMap<String, Object>();
				map.put("title", r.getCheckpointAddress()+"<br/>"
				+r.getCheckpointDate().toString().substring(11)+"<br/>");
				map.put("latitude", r.getLatitude());
				map.put("longitude", r.getLongitude());
				mapList.add(map);
			}
			ObjectMapper objectMapper = new ObjectMapper();
	        String jsonPostions = objectMapper.writeValueAsString(mapList);
	        
	        if(timelineList.size()>1){
				Map<String,Object> map=new HashMap<String, Object>();
				mapList=new ArrayList<Map<String,Object>>();
				map.put("startName", "출발지");
				map.put("startX", timelineList.get(0).getLongitude().toString());
				map.put("startY", timelineList.get(0).getLatitude().toString());
				map.put("startTime", timelineList.get(0).getCheckpointDate()
						.replace("-", "")
						.replace(" ", "")
						.replace(":", "")
						.substring(0, 12));
				map.put("endName", "도착지");
				map.put("endX", timelineList.get(timelineList.size()-1).getLongitude().toString());
				map.put("endY", timelineList.get(timelineList.size()-1).getLatitude().toString());
				map.put("reqCoordType", "WGS84GEO");
		//		map.put("resCoordType", "EPSG3857");
				map.put("resCoordType", "WGS84GEO");
				map.put("searchOption", searchOption);
		        for(int i=1;i<(timelineList.size()-1);i++) {
		    		Map<String,Object> map3=new HashMap<String, Object>();
		        	map3.put("viaPointId", timelineList.get(i).getRecordUserId()+"_"+i);
		        	map3.put("viaPointName", timelineList.get(i).getCheckpointDate());
		        	map3.put("viaX", timelineList.get(i).getLongitude().toString());
		        	map3.put("viaY", timelineList.get(i).getLatitude().toString());
		        	mapList.add(map3);
		        }
		        //경유지가 없으면 오류뜬다. 그래서 도착지에 경유지를 추가한다 
		        if(mapList==null || mapList.isEmpty()) {
		    		Map<String,Object> map3=new HashMap<String, Object>();
		        	map3.put("viaPointId", "stopover");
		        	map3.put("viaPointName", "경유지");
		        	map3.put("viaX", timelineList.get(timelineList.size()-1).getLongitude().toString());
		        	map3.put("viaY", timelineList.get(timelineList.size()-1).getLatitude().toString());
		        	mapList.add(map3);
		        }
		    	map.put("viaPoints", mapList);
		        String jsonParam=objectMapper.writeValueAsString(map);
	
				model.addAttribute("positionParam",jsonParam);
			}
			model.addAttribute("positions",jsonPostions);
		}
		model.addAttribute("timelineCount",timelineList.size());
		
		model.addAttribute("apiKey", kakaoMapApiKey);
		//model.addAttribute("tMapApiKey",tMapApiKey);
		model.addAttribute("restKey",restKey);
		model.addAttribute("userId",userId);
		model.addAttribute("timelineList", timelineList);
		model.addAttribute("selectDay",selectDay);
		return "timeline/getTimelineList";
	}
	
	//getDetailTimeline에서 처리 완료
//	@GetMapping("getSimpleTimeline")
//	public String getSimpleTimeline(Model model,
//			@RequestParam(value="recordNo", required = true) int recordNo,
//			HttpServletRequest request) throws Exception,IOException {
//		Record record=timelineService.getDetailTimeline(recordNo);
//		record=timelineUtil.imojiNameToByte(record);
//		System.out.println("record.getCheckpointDate().toString().substring(0, 10) :"+record.getCheckpointDate().toString().substring(0, 10));
//		model.addAttribute("apiKey", kakaoMapApiKey);
//		model.addAttribute("restKey",restKey);
//		model.addAttribute("record",record);
//		model.addAttribute("selectDay",record.getCheckpointDate().toString().substring(0, 10));
//		return "timeline/getSimpleTimeline";
//	}
	
	@GetMapping("getDetailTimeline")
	public String getDetailTimeline(Model model,
			@RequestParam(name="recordNo", required = true) int recordNo,
			@RequestParam(name="badImageCount", required = false) Integer badImageCount,
			HttpServletRequest request
			) throws Exception,IOException {
		Record record=timelineService.getDetailTimeline(recordNo);
//		record=timelineUtil.imageNameToUrl(record);
//		record=timelineUtil.imojiNameToUrl(record);
//		record=timelineUtil.mediaNameToUrl(record);
		record=timelineUtil.imageNameToByte(record);
		record=timelineUtil.imojiNameToByte(record);
		record=timelineUtil.mediaNameToByte(record);
		if(record.getRecordText()!=null && !record.getRecordText().trim().equals("")) {
			record.setRecordText(textToImage.processImageTags(record.getRecordText()));
		}
		if(badImageCount==null) badImageCount=0;
		
		model.addAttribute("apiKey", kakaoMapApiKey);
		model.addAttribute("restKey",restKey);
		model.addAttribute("updateCountText", TimelineUtil.updateCountToText(record.getUpdateCount()));
		model.addAttribute("badImageCount",badImageCount);
		model.addAttribute("record",record);
		model.addAttribute("selectDay",record.getCheckpointDate().toString().substring(0, 10));
		
		return "timeline/getDetailTimeline";
	}
	
	@GetMapping("updateTimeline")
	public String updateTimelineView(Model model,
			@RequestParam(name="recordNo", required = true) int recordNo) throws Exception,IOException {
		Record record=timelineService.getDetailTimeline(recordNo);
//		record=timelineUtil.imageNameToUrl(record);
//		record=timelineUtil.mediaNameToUrl(record);
		record=timelineUtil.imageNameToByte(record);
		record=timelineUtil.mediaNameToByte(record);
		model.addAttribute("hashtagText",TimelineUtil.hashtagListToText(record.getHashtag()));
		model.addAttribute("category", timelineService.getCategoryList());
		model.addAttribute("apiKey", kakaoMapApiKey);
		model.addAttribute("updateCountText", TimelineUtil.updateCountToText(record.getUpdateCount()));
		model.addAttribute("record",record);
		
		
		 // 추천 // 
		if(record.getRecordText()!=null && !record.getRecordText().trim().equals("")) {
			Recommend recommend = recommendService.getRecordData(record, record.getRecordNo());
			recommend.setPositive(recommendService.getPositive(record.getRecordText()));
			if(recommend.getPositive() > 0) {
				recommendService.addSearchData(record); 
				System.out.println("positive : "+recommend.getPositive());
				recommendService.updateDataset(recommend);
				recommendService.saveDatasetToCSV(recommend, "aitems-8982956307867"); // 추천
			}
			
		}
		//
				
		return "timeline/updateTimeline";
	}
	
	@PostMapping("updateTimeline")
	public String updateTimeline(Model model,
			@ModelAttribute("record") Record record,
			@RequestParam(name="hashtagText",required = false) String hashtagText,
			@RequestParam(name="sharedDateType",required = false) Integer sharedDateType,
			@RequestParam(name="mediaFile",required = false) MultipartFile mediaFile,
			@RequestParam(name="imageFile",required = false) List<MultipartFile> imageFile,
			HttpServletRequest request
			) throws Exception,IOException {
		
//		record.setMediaName( timelineUtil.mediaUrlToName(record.getMediaName()) );
		Map <String,Object> uploadImageMap=timelineUtil.uploadImageFile(record, imageFile);
		record = (Record)uploadImageMap.get("record");
		int badImageCount=(int)uploadImageMap.get("badImageCount");
		record = timelineUtil.uploadMediaFile(record, mediaFile);
		
		record.setHashtag(TimelineUtil.hashtagTextToList(hashtagText, record.getRecordNo()));
		
		record.setUpdateCount(record.getUpdateCount()+1);
//		System.out.println("오류 체크 : "+record.getMediaName()+"/"+record.getImageName()+"/"+record.getRecordText());
		
		if( !(record.getMediaName()==null || record.getMediaName().trim().equals(""))
				|| !(record.getImageName()==null || record.getImageName().isEmpty())
				|| !(record.getRecordText()==null || record.getRecordText().trim().equals("")) ) {
			if(record.getRecordAddDate()==null || record.getRecordAddDate().trim().equals("")) {
				record.setRecordAddDate(LocalDateTime.now(ZoneId.of("Asia/Seoul")).toString().replace("T", " ").split("\\.")[0]);
			}
			record.setTempType(1);
		}else {
			record.setTempType(0);
		}
		//System.out.println("record.getSharedDate() :"+record.getSharedDate());
		if(sharedDateType!=null&&sharedDateType==1) {
			if(ContentFilterUtil.checkBadWord(record.getRecordText()) 
					|| ContentFilterUtil.checkBadWord(record.getRecordTitle())
					|| ContentFilterUtil.checkBadWord(hashtagText) ) {
				record.setSharedDate(null);
			}else {
				if(record.getSharedDate()==null || record.getSharedDate().trim().equals("")) {
					record.setSharedDate(LocalDateTime.now(ZoneId.of("Asia/Seoul")).toString().replace("T", " ").split("\\.")[0]);
				}
			}
		}else {
			record.setSharedDate(null);
		}
		
		record=TimelineUtil.validateRecord(record);
		
		for(KeywordData k: TimelineUtil.calculateKeyword(timelineService.getDetailTimeline(record.getRecordNo()), record)) {
			timelineService.addKeyword(k);
		}
		
		timelineService.updateTimeline(record);
		

		// 추천 //
		if(record.getRecordText()!=null && !record.getRecordText().trim().equals("")) {
			recommendService.addSearchData(record); 
			Recommend recommend = recommendService.getRecordData(record, record.getRecordNo());
			recommend.setPositive(recommendService.getPositive(record.getRecordText()));
			System.out.println("positive : "+recommend.getPositive());
			recommendService.updateDataset(recommend);
			recommendService.saveDatasetToCSV(recommend, "aitems-8982956307867"); // 추천
		}
		//
		
		String uri="?recordNo="+record.getRecordNo()+"&badImageCount="+badImageCount;
		return "redirect:/timeline/getDetailTimeline"+uri;

	}

	@GetMapping("deleteTimeline")
	public String deleteTimeline(Model model,
			@RequestParam(name="recordNo", required = true) int recordNo,
			@RequestParam(name="selectDay", required = true) String selectDay,
			HttpServletRequest request
			) throws Exception,IOException {
		
		Record record = timelineService.getDetailTimeline(recordNo);
		if(record.getMediaName()!=null && !record.getMediaName().trim().equals("")) timelineUtil.deleteMediaFile(record.getMediaName());
		if(record.getImageName()!=null && !record.getImageName().isEmpty()) {
			for(ImageTag image:record.getImageName()) {
				timelineUtil.deleteImageFile(image.getImageTagText());
			}
		}
		for(KeywordData k: TimelineUtil.calculateKeyword(record, 
				Record.builder().recordUserId(record.getRecordUserId()).build())) {
			timelineService.addKeyword(k);
		}
		
		timelineService.deleteTimeline(recordNo);
		String param="?selectDay="+selectDay;
		return "redirect:/timeline/getTimelineList"+param;
	}
	
	@GetMapping("getTimecapsuleList")
	public String getTimecapsuleList(Model model,
			@RequestParam(name="tempType", required = true) int tempType,
			HttpServletRequest request
			) throws Exception,IOException{
		String userId = redisUtil.getSession(request).getUserId();
		
		Search search = Search.builder()
				.userId(userId)
				.tempType(tempType)
				.timecapsuleType(1)
				.limit(pageLimit)
				.currentPage(1)
				.build();
				
		
		model.addAttribute("apiKey", kakaoMapApiKey);
		model.addAttribute("restKey",restKey);
		model.addAttribute("userId",userId);
		model.addAttribute("currentPage",1);
		model.addAttribute("tempType",tempType);//1일시 타임캡슐 기록, 0일시 임시저장된 타임캡슐 기록
		model.addAttribute("timecapsuleList", timelineService.getTimelineList(search));
		return "timeline/getTimecapsuleList";
	}
	
	//getTimecapsuleList에 하나로 합쳤음
//	@GetMapping("getTempTimecapsuleList")
//	public String getTempTimecapsuleList(Model model,
//			HttpServletRequest request
//			) throws Exception,IOException{
//		String userId = redisUtil.getSession(request).getUserId();
//		Search search = Search.builder()
//				.userId(userId)
//				.tempType(0)
//				.timecapsuleType(1)
//				.limit(pageLimit)
//				.currentPage(1)
//				.build();
//		
//		model.addAttribute("apiKey", kakaoMapApiKey);
//		model.addAttribute("restKey",restKey);
//		model.addAttribute("userId",userId);
//		model.addAttribute("currentPage",1);
//		model.addAttribute("checkedSwitch",1);
//		model.addAttribute("timecapsuleList", timelineService.getTimelineList(search));
//		return "timeline/getTimecapsuleList";
//	}
	
	@GetMapping("getDetailTimecapsule")
	public String getDetailTimecapsule(Model model,
			@RequestParam(value="recordNo", required = true) int recordNo,
			HttpServletRequest request
			) throws Exception,IOException {
		Record record=timelineService.getDetailTimeline(recordNo);
//		record=timelineUtil.imageNameToUrl(record);
//		record=timelineUtil.imojiNameToUrl(record);
//		record=timelineUtil.mediaNameToUrl(record);
		record=timelineUtil.imageNameToByte(record);
		record=timelineUtil.imojiNameToByte(record);
		record=timelineUtil.mediaNameToByte(record);
		record.setRecordText(textToImage.processImageTags(record.getRecordText()));
		
		model.addAttribute("apiKey", kakaoMapApiKey);
		model.addAttribute("restKey",restKey);
		model.addAttribute("userId",redisUtil.getSession(request).getUserId());
		model.addAttribute("record",record);
		return "timeline/getDetailTimecapsule";
	}
	
	@GetMapping("addTimecapsule")
	public String addTimecapsuleView(Model model,
			HttpServletRequest request) throws Exception,IOException {
		
		model.addAttribute("restKey",restKey);
		model.addAttribute("apiKey", kakaoMapApiKey);
		model.addAttribute("userId",redisUtil.getSession(request).getUserId());
		model.addAttribute("category", timelineService.getCategoryList());
		return "timeline/addTimecapsule";
	}
	
	@PostMapping("addTimecapsule")
	public String addTimecapsule(Model model,
			@ModelAttribute(name="record") Record record,
			@RequestParam(name="hashtagText",required = false) String hashtagText,
			@RequestParam(name="mediaFile",required = false) MultipartFile mediaFile,
			@RequestParam(name="imageFile",required = false) List<MultipartFile> imageFile,
			HttpServletRequest request
			) throws Exception,IOException {
		if(record.getRecordTitle()==null || record.getRecordTitle().trim().equals("")) {
			if(!(record.getCheckpointAddress()==null || record.getCheckpointAddress().trim().equals("")) 
					&& !(record.getD_DayDate()==null || record.getD_DayDate().trim().equals(""))) {
				record.setRecordTitle(record.getCheckpointAddress()+"_"+record.getD_DayDate());
			}else {
				if(record.getRecordTitle()==null ||record.getRecordTitle().trim().equals("")){
					 record.setRecordTitle("임시저장된 타임캡슐_"
					+LocalDateTime.now(ZoneId.of("Asia/Seoul")).toString().replace("T", " ").split("\\.")[0]);
				}
			}
		}
		record.setUpdateCount(-1);
		record = (Record)timelineUtil.uploadImageFile(record, imageFile).get("record");
		record = timelineUtil.uploadMediaFile(record, mediaFile);
		
		record.setHashtag(TimelineUtil.hashtagTextToList(hashtagText, record.getRecordNo()));
		
		if(record.getTempType()==1 
				|| !(record.getMediaName()==null || record.getMediaName().trim().equals(""))
				|| !(record.getImageName()==null || record.getImageName().isEmpty())
				|| !(record.getRecordText()==null || record.getRecordText().trim().equals("")) ) {
			if(record.getRecordAddDate()==null || record.getRecordAddDate().trim().equals("")) {
				record.setRecordAddDate(LocalDateTime.now(ZoneId.of("Asia/Seoul")).toString().replace("T", " ").split("\\.")[0]);
			}
		}
		
		record=TimelineUtil.validateRecord(record);
		
		timelineService.addTimeline(record);
		if(record.getTempType()==1) {
			String param="?recordNo="+record.getRecordNo();
			return "redirect:/timeline/getDetailTimecapsule"+param;
		}else {
			String param="?tempType="+record.getTempType();
			return "redirect:/timeline/getTimecapsuleList"+param;
		}
	}
	
	@GetMapping("updateTimecapsule")
	public String updateTimecapsuleView(Model model,
			@RequestParam(name="recordNo", required = true) Integer recordNo) throws Exception,IOException {
		Record record = timelineService.getDetailTimeline(recordNo);
		record=timelineUtil.imageNameToByte(record);
		record=timelineUtil.mediaNameToByte(record);
		model.addAttribute("hashtagText",TimelineUtil.hashtagListToText(record.getHashtag()));
		model.addAttribute("category", timelineService.getCategoryList());
		model.addAttribute("restKey",restKey);
		model.addAttribute("apiKey", kakaoMapApiKey);
		model.addAttribute("record",record);
		return "timeline/updateTimecapsule";
	}
	
	@PostMapping("updateTimecapsule")
	public String updateTimecapsule(Model model,
			@ModelAttribute("record") Record record,
			@RequestParam(name="hashtagText",required = false) String hashtagText,
			@RequestParam(name="mediaFile",required = false) MultipartFile mediaFile,
			@RequestParam(name="imageFile",required = false) List<MultipartFile> imageFile,
			HttpServletRequest request
			) throws Exception,IOException {
//		record.setMediaName( timelineUtil.mediaUrlToName(record.getMediaName()) );
		record = (Record)timelineUtil.uploadImageFile(record, imageFile).get("record");
		record = timelineUtil.uploadMediaFile(record, mediaFile);
		
		record.setHashtag(TimelineUtil.hashtagTextToList(hashtagText, record.getRecordNo()));
		
		if(record.getTempType()==1 
				|| !(record.getMediaName()==null || record.getMediaName().trim().equals(""))
				|| !(record.getImageName()==null || record.getImageName().isEmpty())
				|| !(record.getRecordText()==null || record.getRecordText().trim().equals("")) ) {
			if(record.getRecordAddDate()==null || record.getRecordAddDate().trim().equals("")) {
				record.setRecordAddDate(LocalDateTime.now(ZoneId.of("Asia/Seoul")).toString().replace("T", " ").split("\\.")[0]);
			}
		}
		
		record=TimelineUtil.validateRecord(record);
		
		timelineService.updateTimeline(record);
		if(record.getTempType()==1) {
			String param="?recordNo="+record.getRecordNo();
			return "redirect:/timeline/getDetailTimecapsule"+param;
		}else {
			String param="?tempType="+record.getTempType();
			return "redirect:/timeline/getTimecapsuleList"+param;
		}
	}

	@GetMapping("deleteTimecapsule")
	public String deleteTimecapsule(Model model,
			@RequestParam(value="recordNo", required = true) int recordNo,
			HttpServletRequest request
			) throws Exception,IOException {
		Record record = timelineService.getDetailTimeline(recordNo);
		if(record.getMediaName()!=null && !record.getMediaName().trim().equals("")) timelineUtil.deleteMediaFile(record.getMediaName());
		if(record.getImageName()!=null && !record.getImageName().isEmpty()) {
			for(ImageTag image:record.getImageName()) {
				timelineUtil.deleteImageFile(image.getImageTagText());
			}
		}
		
		timelineService.deleteTimeline(recordNo);
		String param="?tempType="+record.getTempType();
		return "redirect:/timeline/getTimecapsuleList"+param;
	}
	
	@GetMapping("getSummaryRecord")
	public String getSummaryRecord(Model model,
			@RequestParam(name="searchCondition", required = true) int searchCondition,
			@RequestParam(name="selectDate", required = false) String selectDate,
			HttpServletRequest request) throws Exception,IOException{
		String userId = redisUtil.getSession(request).getUserId();
		
		if(selectDate==null) {
			selectDate=LocalDateTime.now(ZoneId.of("Asia/Seoul")).toString().split("\\.")[0];
		}else {
			selectDate+="T"+LocalTime.now(ZoneId.of("Asia/Seoul")).toString().split("\\.")[0];
		}
		
		Search search = Search.builder()
				.userId(userId)
				.searchCondition(searchCondition)
				.searchKeyword(selectDate)
				.build();
		
		String searchDate = timelineService.getSummaryDate(search);
		List<SummaryRecordDto> recordList=new ArrayList<SummaryRecordDto>();
		if(searchDate!=null) {
			searchDate=searchDate.substring(0, 10);
			search.setSelectDate(Date.valueOf(searchDate));
			
			recordList = timelineUtil.summaryFileNameToByte(timelineService.getSummaryRecord(search));
			
			List<String> dateList=timelineService.getSummaryDateList(userId);
			
			model.addAttribute("dateList",dateList);
			model.addAttribute("startDay", dateList.get(0));
			model.addAttribute("endDay", dateList.get(dateList.size()-1) );
			model.addAttribute("selectDate", searchDate);
		}
		
		System.out.println("recordList : " + recordList);
		model.addAttribute("apiKey", kakaoMapApiKey);
		model.addAttribute("restKey",restKey);
		model.addAttribute("recordList", recordList);
		return "timeline/getSummaryRecord";
	}
	
	@GetMapping("getKeywordList")
	public String getKeywordList(Model model,
			HttpServletRequest request
			) throws Exception,IOException {
		String userId = redisUtil.getSession(request).getUserId();
		
		List<Map<String,Object>> keywordList= new ArrayList<Map<String,Object>>();
		for(KeywordData k :timelineService.getKeywordList(userId)) {
			Map<String,Object> map=new HashMap<String, Object>();
			map.put("word", k.getKeyword());
			map.put("size", k.getKeywordCount()*8+10);
			keywordList.add(map);
		}
		ObjectMapper objectMapper = new ObjectMapper();
		String keywordJSONString=objectMapper.writeValueAsString(keywordList);
		
		System.out.println("keywordList : "+keywordJSONString);
		model.addAttribute("keywordList",keywordJSONString);
		return "timeline/getKeywordList";
	}
	
	@GetMapping("getUserCategoryList")
	public String getUserCategoryList(Model model) throws Exception,IOException{
		model.addAttribute("categoryList",timelineUtil.categoryImojiListToByte(
						timelineService.getCategoryList()));
		return "timeline/getUserCategoryList";
	}
	
	@GetMapping("getAdminCategoryList")
	public String getAdminCategoryList(Model model) throws Exception,IOException{
		List<Category> categoryList = timelineUtil.categoryImojiListToByte(
				timelineService.getCategoryList());
		System.out.println(categoryList);
		model.addAttribute("categoryList", categoryList);
		return "timeline/admin/getAdminCategoryList";
	}
	
	@GetMapping("addCategory")
	public String addCategoryView() throws Exception,IOException {
		return "timeline/admin/addCategory";
	}
	
	@GetMapping("updateCategory")
	public String updateCategoryView(Model model,@RequestParam(name="categoryNo",required = true) int categoryNo) throws Exception,IOException {
		model.addAttribute("category",timelineUtil.categoryImojiNameToByte(timelineService.getCategory(categoryNo)));
		return "timeline/admin/updateCategory";
	}
	
	@GetMapping("addTimelienForRecord")
	public String addTimelienForRecordView(Model model,
			HttpServletRequest request) throws Exception,IOException {
		
		model.addAttribute("restKey",restKey);
		model.addAttribute("apiKey", kakaoMapApiKey);
		model.addAttribute("userId",redisUtil.getSession(request).getUserId());
		model.addAttribute("category", timelineService.getCategoryList());
		return "timeline/addTimelienForRecord";
	}
	
	@PostMapping("addTimelienForRecord")
	public String addTimelienForRecord(Model model,
			@ModelAttribute(name="record") Record record,
			@RequestParam(name="hashtagText",required = false) String hashtagText,
			@RequestParam(name="mediaFile",required = false) MultipartFile mediaFile,
			@RequestParam(name="imageFile",required = false) List<MultipartFile> imageFile,
			HttpServletRequest request
			) throws Exception,IOException {
				if(record.getRecordTitle()==null || record.getRecordTitle().trim().equals("")) {
					if(record.getTimecapsuleType()==0) {
						record.setRecordTitle(record.getCheckpointAddress()+"_"+record.getCheckpointDate());
					}else {
						if(!(record.getCheckpointAddress()==null || record.getCheckpointAddress().trim().equals("")) 
								&& !(record.getD_DayDate()==null || record.getD_DayDate().trim().equals(""))) {
							record.setRecordTitle(record.getCheckpointAddress()+"_"+record.getD_DayDate());
						}else {
							if(record.getRecordTitle()==null ||record.getRecordTitle().trim().equals("")){
								 record.setRecordTitle("임시저장된 타임캡슐_"
								+LocalDateTime.now(ZoneId.of("Asia/Seoul")).toString().replace("T", " ").split("\\.")[0]);
							}
						}
					}
				}
				
				
				record.setUpdateCount(-1);
				record.setCategoryNo(0);
				if(record.getD_DayDate()==null || record.getD_DayDate().trim().equals("")) record.setD_DayDate(null);
				if(record.getCheckpointDate()==null || record.getCheckpointDate().trim().equals("")) record.setCheckpointDate(null);
				
				int recordNo=timelineService.addTimeline(record);
				String param="?recordNo="+recordNo;

				if(record.getTimecapsuleType()==0) {
					return "redirect:/timeline/updateTimeline"+param;
				}else {
					return "redirect:/timeline/updateTimecapsule"+param;
				}
	}
	
//	@GetMapping("addVoiceToText")
//	public String addVoiceToText() throws Exception,IOException {
//		return "timeline/addVoiceToText";
//	}
	
	
	@GetMapping({"getDetailTimeline3"})
	public void getDetailTimeline3(Model model, String userId) throws Exception,IOException {
		model.addAttribute("record",timelineService.getDetailTimeline(1));
		model.addAttribute("record2",timelineService.getDetailSharedRecord(1, userId));
	}
	
	@GetMapping({"getDetailTimeline2"})
	public void getTimelineList2(Model model) throws Exception,IOException {
		Search search;
//		search=Search.builder()
//				.currentPage(1)
//				.limit(3)
//				.build();
//		model.addAttribute("list",timelineService.getTimelineList(search));
//		search=Search.builder()
//				.currentPage(1)
//				.limit(3)
//				.sharedType(1)
//				.tempType(1)
//				.timecapsuleType(0)
//				.build();
//		model.addAttribute("list2",timelineService.getTimelineList(search));
//		search=Search.builder()
//				.currentPage(1)
//				.limit(3)
//				.sharedType(0)
//				.tempType(0)
//				.timecapsuleType(0)
//				.build();
//		model.addAttribute("list3",timelineService.getTimelineList(search));
//		search=Search.builder()
//				.currentPage(3)
//				.limit(3)
//				.sharedType(0)
//				.tempType(1)
//				.timecapsuleType(0)
//				.build();
//		model.addAttribute("list4",timelineService.getTimelineList(search));
//		search=Search.builder()
//				.currentPage(3)
//				.limit(3)
//				.sharedType(1)
//				.tempType(1)
//				.timecapsuleType(0)
//				.build();
//		model.addAttribute("list5",timelineService.getTimelineList(search));
//		search=Search.builder()
//				.currentPage(1)
//				.limit(5)
//				.sharedType(0)
//				.tempType(0)
//				.timecapsuleType(1)
//				.build();
//		model.addAttribute("list6",timelineService.getTimelineList(search));
//		//d_day보다 현재 날짜가 위에 있으면 갖고오는 조건식
//		search=Search.builder()
//				.currentPage(1)
//				.limit(3)
//				.sharedType(0)
//				.tempType(1)
//				.timecapsuleType(1)
//				.build();
//		model.addAttribute("list7",timelineService.getTimelineList(search));
		//대민 지원
		search=Search.builder()
				.userId("user1")
				.currentPage(1)
				.limit(15)
				.logsType(0)
				.build();
		model.addAttribute("list8",timelineService.getProfileTimelineList(search));
		model.addAttribute("list8_count", timelineService.getProfileTimelineCount(search));
		
		search=Search.builder()
				.userId("user1")
				.currentPage(1)
				.limit(5)
				.logsType(1)
				.build();
		model.addAttribute("list8_1",timelineService.getProfileTimelineList(search));
		search=Search.builder()
				.userId("user1")
				.currentPage(1)
				.limit(5)
				.logsType(2)
				.build();
		model.addAttribute("list8_2",timelineService.getProfileTimelineList(search));
//		//재용 지원
//		search=Search.builder()
//				.currentPage(1)
//				.limit(5)
//				.build();
//		model.addAttribute("list9",timelineService.getSharedRecordList(search));
//		search=Search.builder()
//				.currentPage(2)
//				.limit(5)
//				.build();
//		model.addAttribute("list10",timelineService.getSharedRecordList(search));
//		search=Search.builder()
//				.timecapsuleType(0)
//				.selectDay1("2024-06-04 00:00:00")
//				.selectDay2("2024-06-05 00:00:00")
//				.build();
//		model.addAttribute("list11",timelineService.getTimelineList(search));
//		
		//성문 지원===========================================
//		search=Search.builder()
//	 			.latitude(33.450701)
//	 			.longitude(126.570667)
//	 			.radius(110)
//	 			.limit(10)
//	 			.userId("user1")
//	 			.followType(1)
//				.build();
//		model.addAttribute("mapList1",timelineService.getMapRecordList(search));
//		search=Search.builder()
//				.latitude(33.450701)
//	 			.longitude(126.570667)
//	 			.radius(110)
//	 			.limit(10)
//	 			.userId("user1")
//	 			.sharedType(1)
//	 			.searchKeyword("22")
//				.build();
//		model.addAttribute("mapList2",timelineService.getMapRecordList(search));
//		search=Search.builder()
//				.latitude(37.56789)
//	 			.longitude(127.345678)
//	 			.radius(1100)
//	 			.limit(10)
//	 			.userId("user1")
//	 			.privateType(1)
//				.build();
//		model.addAttribute("mapList3",timelineService.getMapRecordList(search));
//		search=Search.builder()
//				.latitude(37.56789)
//	 			.longitude(127.345678)
//	 			.radius(1100)
//	 			.limit(10)
//	 			.userId("user1")
//	 			.privateType(1)
//	 			.searchKeyword("나가")
//				.build();
//		model.addAttribute("mapList3_2",timelineService.getMapRecordList(search));
//		search=Search.builder()
//				.latitude(37.56789)
//	 			.longitude(127.345678)
//	 			.radius(1100)
//	 			.limit(10)
//	 			.userId("user1")
//	 			.privateType(1)
//	 			.searchKeyword("나가")
//	 			.categoryNo(2)
//				.build();
//		model.addAttribute("mapList3_3",timelineService.getMapRecordList(search));
//		search=Search.builder()
//				.latitude(37.56789)
//	 			.longitude(127.345678)
//	 			.radius(1100)
//	 			.limit(10)
//	 			.userId("user1")
//	 			.privateType(1)
//	 			.categoryNo(3)
//				.build();
//		model.addAttribute("mapList3_4",timelineService.getMapRecordList(search));
//		search=Search.builder()
//				.latitude(37.4794143)
//	 			.longitude(127.020817)
//	 			.privateType(1)
//	 			.followType(1)
//	 			.sharedType(1)
//	 			.userId("user1")
//	 			.radius(1100)
//	 			.limit(100)
//				.build();
//		model.addAttribute("mapList4",timelineService.getMapRecordList(search));
//		//===========================================
//		search=Search.builder()
//				.userId("user1")
//				.selectDate(Date.valueOf("2024-05-29"))
//				.build();
//		model.addAttribute("list16",timelineService.getSummaryRecord(search));
	}
	
	
	//아래 미사용
//	@GetMapping({"getDetailTimeline2"})
//	public void getDetailTimeline2(Model model) throws Exception,IOException {
//		model.addAttribute("record",timelineService.getDetailTimeline2(1));
//	}
}
