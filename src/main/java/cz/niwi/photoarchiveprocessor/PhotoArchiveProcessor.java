package cz.niwi.photoarchiveprocessor;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;

import java.io.File;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Base photo processor.
 */
class PhotoArchiveProcessor {

    /**
     * Path to the root source photo directory.
     */
    private File rootPath;
    /**
     * Date marker which could be used when we want to process just certain year/month/day.
     */
    private DateMarker presetDirectoryDateMarker;
    /**
     * Flag which denotes that in rootPath is actually not the root photo directory, but that it exact path.
     * It is necessary to use this flag with presetDateMarker.
     */
    private boolean processExactPath;
    /**
     * Parser of directories names.
     */
    private DirectoryDateParser directoryDateParser = new NiwiDirectoryDateParser();

    /**
     * Constructor.
     * @param rootPath
     */
    public PhotoArchiveProcessor(String rootPath) {
        this(rootPath, null);
    }

    /**
     * Constructor.
     * @param rootPath
     * @param presetDirectoryDateMarker
     */
    public PhotoArchiveProcessor(String rootPath, DateMarker presetDirectoryDateMarker) {
        this(rootPath, presetDirectoryDateMarker, false);
    }

    /**
     * Constructor.
     * @param rootPath
     * @param presetDirectoryDateMarker
     * @param processExactPath
     */
    public PhotoArchiveProcessor(String rootPath, DateMarker presetDirectoryDateMarker, boolean processExactPath) {
        if (processExactPath && presetDirectoryDateMarker == null)
            throw new InvalidParameterException("Exact path must be set with pre-set date marker only!");
        this.rootPath = new File(rootPath);
        this.presetDirectoryDateMarker = presetDirectoryDateMarker;
        this.processExactPath = processExactPath;
    }

    protected File getRootPath() { return rootPath; }
    protected DirectoryDateParser getDirectoryDateParser() { return directoryDateParser; }

    /**
     * Determines whether some year is preset to process.
     * @return
     */
    protected boolean hasPresetYear() {
        return this.presetDirectoryDateMarker != null && this.presetDirectoryDateMarker.getYear() > 0;
    }

    /**
     * Determines whether some month is preset to process.
     * @return
     */
    protected boolean hasPresetMonth() {
        return this.presetDirectoryDateMarker != null && this.presetDirectoryDateMarker.getMonth() > 0;
    }

    /**
     * Determines whether some day is preset to process.
     * @return
     */
    protected boolean hasPresetDay() {
        return this.presetDirectoryDateMarker != null && this.presetDirectoryDateMarker.getDay() > 0;
    }


    /**
     * Process the data.
     */
    public void process() {
        if (this.processExactPath) {
            if (this.presetDirectoryDateMarker.hasDay())
                this.processDayDir(this.getRootPath(), this.presetDirectoryDateMarker, false);
            else if (this.presetDirectoryDateMarker.hasMonth())
                this.processMonthDir(this.getRootPath(), this.presetDirectoryDateMarker);
            else
                this.processYearDir(this.getRootPath(), this.presetDirectoryDateMarker);
            return;
        }

        System.out.println("Starting to scan the root path: " + this.getRootPath());

        List<String> dirItemsNames = Arrays.asList(this.getRootPath().list());
        Collections.sort(dirItemsNames);

        boolean yearFound = false;
        for(String yearDirName : dirItemsNames) {
            File f = new File(this.getRootPath(), yearDirName);
            if (!f.isDirectory())
                continue;
            short year = this.getDirectoryDateParser().parseYear(yearDirName);
            if (year != 0 && (!this.hasPresetYear() || this.presetDirectoryDateMarker.getYear() == year)) {
                yearFound = true;
                this.processYearDir(f, new DateMarker(year));
            }
        }
        if (this.hasPresetYear() && !yearFound)
            System.err.println("Preset year not found.");
    }


    /**
     * Process one single year directory.
     * @param yearDir
     * @param dateMarker Current date marker
     */
    private void processYearDir(File yearDir, DateMarker dateMarker) {
        System.out.println("Processing the year: " + dateMarker.getYear());

        List<String> dirItemsNames = Arrays.asList(yearDir.list());
        Collections.sort(dirItemsNames);

        boolean monthFound = false;
        for(String monthDirName : dirItemsNames) {
            File f = new File(yearDir, monthDirName);
            if (!f.isDirectory())
                continue;
            byte month = this.getDirectoryDateParser().parseMonth(monthDirName);
            if (month != 0 && (!this.hasPresetMonth() || this.presetDirectoryDateMarker.getMonth() == month)) {
                monthFound = true;
                this.processMonthDir(f, dateMarker.cloneWithMonth(month));
            }
            else
                System.out.println("Non valid " + (f.isDirectory() ? "directory" : "file") + ": " + f.getName());
        }
        if (this.hasPresetMonth() && !monthFound)
            System.err.println("Preset month not found.");
    }

    /**
     * Process one single month directory.
     * @param monthDir
     * @param dateMarker Current date marker
     */
    private void processMonthDir(File monthDir, DateMarker dateMarker) {
        System.out.println("Processing the month: " + dateMarker.getMonth());

        List<String> dirItemsNames = Arrays.asList(monthDir.list());
        Collections.sort(dirItemsNames);

        boolean dayFound = false;
        for(String dayDirName : dirItemsNames) {
            File f = new File(monthDir, dayDirName);
            if (!f.isDirectory())
                continue;
            byte day = this.getDirectoryDateParser().parseDay(dayDirName);
            if (day != 0 && (!this.hasPresetDay() || this.presetDirectoryDateMarker.getDay() == day)) {
                dayFound = true;
                this.processDayDir(f, dateMarker.cloneWithDay(day), false);
            }
            else
                System.out.println("Non valid " + (f.isDirectory() ? "directory" : "file") + ": " + f.getName());
        }
        if (this.hasPresetDay() && !dayFound)
            System.err.println("Preset day not found.");
    }


    /**
     * Process one single day directory.
     * @param dayDir
     * @param dateMarker Current date marker
     * @param isDaySubdir Is subdirectory in day directory?
     */
    private void processDayDir(File dayDir, DateMarker dateMarker, boolean isDaySubdir) {
        File[] files = dayDir.listFiles();
        int matched = 0;
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            if (f.isDirectory()) {
                if (isDaySubdir) {
                    System.err.println("Days could not contain two levels of subdirectories: " + f.getPath());
                    continue;
                }

                System.out.println("Processing day subdirectory: " + f.getName());
                this.processDayDir(f, dateMarker, true);
                continue;
            }
            if (this.processFile(f, dateMarker, isDaySubdir))
                matched++;
        }
        if (matched > 0)
            System.out.println("MATCHED " + matched);
    }


    /**
     * Process single file.
     * @param file
     * @param dateMarker
     * @param isDaySubdir
     * @return
     */
    protected boolean processFile(File file, DateMarker dateMarker, boolean isDaySubdir) {
        System.out.println("PROCESSED image: " + file.getName());
        return true;
    }


    /**
     * Process single file.
     * @param file
     * @return
     */
    static boolean fileHasTag(File file, String tag) {

        if (tag == null)
            return false;

        // Skip all except JPEG files
        String filenameLowercase = file.getName().toLowerCase();
        if (!PhotoArchiveProcessor.fileHasExtension(file, Arrays.asList("jpg", "jpeg")))
            return false;

        Metadata metadata;
        try {
            metadata = ImageMetadataReader.readMetadata(file);
        } catch (ImageProcessingException e) {
            System.out.println("Probably not image file: " + file.getName());
            return false;
        } catch (IOException e) {
            System.out.println("File reading error: " + file.getName());
            return false;
        }

        for (Directory metadataDir : metadata.getDirectories()) {
            if (metadataDir.getName().equals("IPTC")) {
                for (Tag currentTag : metadataDir.getTags()) {
                    if (currentTag.getTagName().equals("Keywords") &&
                            currentTag.getDescription().matches(".*\\b" + tag + "\\b.*")) {
                        return true;
                    }
                }
            }
        }

        return false;
    }


    static boolean fileHasExtension(File file, List<String> extensionList) {
        String extension = "";
        String fileName = file.getName().toLowerCase();
        if (fileName.lastIndexOf(".") != -1 && fileName.lastIndexOf(".") != 0)
            extension = fileName.substring(fileName.lastIndexOf(".")+1);
        return extensionList.contains(extension);
    }


    /**
     * Main program entrypoint.
     * @param args
     */
    public static void main(String[] args) {
        // TODO testing value - should be obtained as program parameter
        String testRootPath = "/Users/miroslav.kvasnica/Pictures/camera";
        PhotoArchiveProcessor syncer = new FTPSyncProcessor(testRootPath);
        syncer.process();
    }
}