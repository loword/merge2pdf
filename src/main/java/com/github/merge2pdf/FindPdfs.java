package com.github.merge2pdf;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class FindPdfs {

    public static List<File> findToPaths(String rootPath) throws IOException {
//        return Files.walk(Paths.get(rootPath))
//                .filter(x -> FilenameUtils.getExtension(x.toString()).equals("pdf"))
//                .map(Path::toFile)
//                .map(path -> path.getFileName().toString())
//                .collect(Collectors.toList());
        Date now = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");//设置日期格式
        String current = dateFormat.format(now);//格式化然后放入字符串中
        return Files.walk(Paths.get(rootPath))
                .filter(s -> s.toString().contains(current))
                .filter(x -> FilenameUtils.getExtension(x.toString()).equals("pdf"))
                .map(Path::toFile)
                .collect(Collectors.toList());
    }

    public static List<File> findFromPaths(String rootPath) throws IOException {
        List<File> list = new ArrayList<>();
        Date now = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");//设置日期格式
        Calendar c = Calendar.getInstance();
        for (int i = 6; i >= 0; i--) {
            //过去七天
            c.setTime(now);
            c.add(Calendar.DATE, -i);
            Date d = c.getTime();
            String current = dateFormat.format(d);//格式化然后放入字符串中
            list.addAll(Files.walk(Paths.get(rootPath))
                    .filter(Files::isRegularFile)
                    .filter(s -> s.toString().contains(current))
                    .map(Path::toFile)
                    .collect(Collectors.toList()));
        }
        return list;
    }
}