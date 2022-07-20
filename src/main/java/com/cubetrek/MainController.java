package com.cubetrek;

import com.cubetrek.database.*;
import com.cubetrek.registration.UserDto;
import com.cubetrek.upload.*;
import com.cubetrek.registration.UserRegistrationService;
import com.cubetrek.viewer.TrackGeojson;
import com.cubetrek.viewer.TrackViewerService;
import com.sunlocator.topolibrary.LatLonBoundingBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


@Controller
public class MainController {
    @Autowired
    private UserRegistrationService userRegistrationService;

    @Autowired
    private StorageService storageService;

    @Autowired
    private GeographyService geographyService;


    @Autowired
    private TrackViewerService trackViewerService;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private TrackMetadataRepository trackMetadataRepository;

    @Autowired
    private OsmPeaksRepository osmPeaksRepository;

    Logger logger = LoggerFactory.getLogger(MainController.class);


    @GetMapping("/registration")
    public String showRegistrationForm(WebRequest request, Model model) {
        System.out.println("get registration");
        UserDto userDto = new UserDto();
        model.addAttribute("user", userDto);
        return "registration";
    }

    @PostMapping("/registration")
    public ModelAndView registerUserAccount(
            @ModelAttribute("user") UserDto userDto,
            HttpServletRequest request, Errors errors) {
        Users registered = userRegistrationService.register(userDto);

        return new ModelAndView("successRegister", "user", userDto);
    }

    @GetMapping("/successRegister")
    public String successRegister() {
        return "successRegister";
    }


    @GetMapping("/")
    public String index(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof AnonymousAuthenticationToken)
            return "index";
        else {
            Users user = (Users)authentication.getPrincipal();
            model.addAttribute("user", user);
            model.addAttribute("tracks", trackMetadataRepository.findByOwner(user));
            return "dashboard";
        }
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @RequestMapping(value="/logout", method = RequestMethod.GET)
    public String logoutPage (HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null){
            new SecurityContextLogoutHandler().logout(request, response, auth);
        }
        return "redirect:/login?logout";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();
        model.addAttribute("user", user);
        model.addAttribute("tracks", trackMetadataRepository.findByOwner(user));

        return "dashboard";
    }

    @GetMapping("/upload")
    public String showUploadForm(WebRequest request, Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();
        model.addAttribute("user", user);

        return "upload";
    }


    @ResponseBody
    @PostMapping(value = "/upload", produces = "application/json")
    public UploadResponse uploadFile(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes, Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Users user = (Users)authentication.getPrincipal();
        return storageService.store(user, file);
    }

    @ResponseBody
    @PostMapping(value = "/upload_anonymous", produces = "application/json")
    public UploadResponse uploadFileAnonymously(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes, Model model) {
        return storageService.store(null, file);
    }

    @GetMapping(value="/view/{itemid}")
    public String viewTrack(@PathVariable("itemid") long trackid, Model model)
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return trackViewerService.mapView3D(authentication, trackid, model);
    }

    @ResponseBody
    @GetMapping(value = "/api/geojson/{itemid}.geojson", produces = "application/json")
    public TrackGeojson getSimplifiedTrackGeoJson(@PathVariable("itemid") long trackid, Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return trackViewerService.getTrackGeojson(authentication, trackid);
    }

    @ResponseBody
    @GetMapping(value = "/api/gltf/{itemid}.gltf", produces = "text/plain")
    public String getGLTF(@PathVariable("itemid") long trackid, Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return trackViewerService.getGLTF(authentication, trackid);
    }

    /**
    @ResponseBody
    @RequestMapping(value = "/api/gltf/map/{zoom}/{x}/{y}.png", produces = "image/png")
    public HttpEntity<byte[]> getGLTF(@PathVariable("zoom") int zoom, @PathVariable("x") int x, @PathVariable("y") int y, Model model) {

        byte[] image = null;
        try {
            //URL url = new URL(String.format("https://api.maptiler.com/maps/basic/%d/%d/%d.png?key=Nq5vDCKAnSrurDLNgtSI", zoom, x,y)); //Sun Locator style map (no shading)
            URL url = new URL(String.format("https://api.maptiler.com/maps/ch-swisstopo-lbm/%d/%d/%d.png?key=Nq5vDCKAnSrurDLNgtSI", zoom, x,y)); //Swiss Topo style map (shading)
            InputStream in = new BufferedInputStream(url.openStream());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n = 0;
            while (-1 != (n = in.read(buf))) {
                out.write(buf, 0, n);
            }
            out.close();
            in.close();
            image = out.toByteArray();
        } catch (IOException ioException) {

        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setContentLength(image.length);

        return new HttpEntity<byte[]>(image, headers);

    }

    **/

    @RequestMapping(value = "/api/gltf/map/{type}/{zoom}/{x}/{y}.png", produces = "image/png")
    public void getGLTF(@PathVariable("type") String type, @PathVariable("zoom") int zoom, @PathVariable("x") int x, @PathVariable("y") int y, HttpServletResponse response) {
        String mapaccession = switch (type) {
            case "winter" ->
                    String.format("https://api.maptiler.com/maps/winter/%d/%d/%d.png?key=Nq5vDCKAnSrurDLNgtSI", zoom, x, y);
            case "satellite" ->
                    String.format("https://api.maptiler.com/tiles/satellite-v2/%d/%d/%d.jpg?key=Nq5vDCKAnSrurDLNgtSI", zoom, x, y);
            case "satellite_ch" ->
                    String.format("https://wmts.geo.admin.ch/1.0.0/ch.swisstopo.swissimage/default/current/3857/%d/%d/%d.jpeg", zoom, x, y);
            case "standard" ->
                    String.format("https://api.maptiler.com/maps/ch-swisstopo-lbm/%d/%d/%d.png?key=Nq5vDCKAnSrurDLNgtSI", zoom, x, y);
            default ->
                    String.format("https://api.maptiler.com/maps/ch-swisstopo-lbm/%d/%d/%d.png?key=Nq5vDCKAnSrurDLNgtSI", zoom, x, y);
        };

        response.setHeader("Location", mapaccession);
        response.setStatus(302);
    }

    @ResponseBody
    @GetMapping(value = "/api/peaks/nbound={nbound}&sbound={sbound}&wbound={wbound}&ebound={ebound}", produces = "application/json")
    //@JsonSerialize(using = OsmPeaks.OsmPeaksListSerializer.class)
    public GeographyService.OsmPeakList getPeaksWithinBBox(@PathVariable("nbound") double nbound, @PathVariable("sbound") double sbound, @PathVariable("wbound") double wbound, @PathVariable("ebound") double ebound) {
        LatLonBoundingBox bbox = new LatLonBoundingBox(nbound, sbound, wbound, ebound);
        return geographyService.findPeaksWithinBBox(bbox);
    }

    @ResponseBody
    @RequestMapping(value="/api/modify")
    public UpdateTrackmetadataResponse updateTrackmetadata(@RequestBody EditTrackmetadataDto editTrackmetadataDto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return storageService.editTrackmetadata(authentication, editTrackmetadataDto);
    }

    @Transactional
    @ResponseBody
    @GetMapping(value="/api/modify/id={id}&favorite={favorite}")
    public UpdateTrackmetadataResponse updateTrackFavorite(@PathVariable("id") long id, @PathVariable("favorite") boolean favorite) {
        isWriteAccessAllowed(SecurityContextHolder.getContext().getAuthentication(), id);
        trackMetadataRepository.updateTrackFavorite(id, favorite);
        if (favorite)
            trackMetadataRepository.updateTrackHidden(id, false);
        return new UpdateTrackmetadataResponse(true);
    }

    @Transactional
    @ResponseBody
    @GetMapping(value="/api/modify/id={id}&hidden={hidden}")
    public UpdateTrackmetadataResponse updateTrackHidden(@PathVariable("id") long id, @PathVariable("hidden") boolean hidden) {
        isWriteAccessAllowed(SecurityContextHolder.getContext().getAuthentication(), id);
        trackMetadataRepository.updateTrackHidden(id, hidden);
        if (hidden) //if hidden, it can't be favorited
            trackMetadataRepository.updateTrackFavorite(id, false);
        return new UpdateTrackmetadataResponse(true);
    }

    @Transactional
    @ResponseBody
    @GetMapping(value="/api/modify/id={id}&sharing={sharing}")
    public UpdateTrackmetadataResponse modifySharing(@PathVariable("id") long id, @PathVariable("sharing") TrackMetadata.Sharing sharing) {
        isWriteAccessAllowed(SecurityContextHolder.getContext().getAuthentication(), id);
        trackMetadataRepository.updateTrackSharing(id, sharing);
        return new UpdateTrackmetadataResponse(true);
    }

    @Transactional
    @ResponseBody
    @DeleteMapping(value="/api/modify/id={id}")
    public UpdateTrackmetadataResponse deleteTrack(@PathVariable("id") long id) {
        isWriteAccessAllowed(SecurityContextHolder.getContext().getAuthentication(), id);
        trackMetadataRepository.deleteById(id);
        logger.info("Delete ID '"+id+"'");
        return new UpdateTrackmetadataResponse(true);
    }

    private void isWriteAccessAllowed(Authentication authentication, long trackId) {
        boolean writeAccess;
        if (authentication instanceof AnonymousAuthenticationToken) //not logged in
            writeAccess = false;
        else {
            Users user = (Users) authentication.getPrincipal();
            long ownerid = trackMetadataRepository.getOwnerId(trackId);
            writeAccess = ownerid == user.getId();
        }
        if (!writeAccess)
            throw new ExceptionHandling.TrackViewerException(TrackViewerService.noAccessMessage);
    }

}