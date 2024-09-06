package com.xiaoyi.leaveTime;

import lombok.extern.slf4j.Slf4j;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.component.CalendarComponent;
import net.fortuna.ical4j.util.MapTimeZoneCache;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@Slf4j
public class ICSParser {
    private static final Map<LocalDate, SpecialDay> specialDayMap = new HashMap<>();
    private static final int DEfAULT = 0, MSTART = 9, MEND = 12, ASTART = 14, AEND = 18;
    private static final String WORKDAY = "ALTERNATE-WORKDAY";

    record SpecialDay(String name, String type) {
    }

    static {
        parseICS();
    }

    public static void main(String[] args) {
        double sum1 = workTime("2024-09-13 09:00:00", "2024-09-18 18:00:00");
        System.out.println(sum1);

        double sum2 = workTime("2024-02-02 09:00:00", "2024-02-02 12:00:00");
        System.out.println(sum2);
    }

    @GetMapping("/leaveTime")
    public ResponseEntity<Double> leaveTime(String start, String end) {
        return start.isEmpty() || end.isEmpty() ? ResponseEntity.ok(0.0) : ResponseEntity.ok(workTime(start, end));
    }

    public static double workTime(String startDt, String endDt) {
        ZonedDateTime startZone;
        ZonedDateTime endZone;
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            startZone = LocalDateTime.parse(startDt, formatter)
                    .atZone(ZoneId.of("UTC"));
            endZone = LocalDateTime.parse(endDt, formatter)
                    .atZone(ZoneId.of("UTC"));
        } catch (DateTimeParseException e) {
            return -1;
        }

        double workHours = 0;
        ZonedDateTime currenDt = startZone;

        while (currenDt.isBefore(endZone)) {
            if (isWorkDate(currenDt)) {
                if (isWorkHour(currenDt)) {
                    workHours++;
                }
            }
            currenDt = currenDt.plusHours(1);
        }
        return reverseRound(workHours / 7.0 * 8);
    }

    private static double reverseRound(double v) {
        return v - (double) (int) v > 0.5
                ? Math.floor(v)
                : Math.ceil(v);
    }

    private static boolean isWorkHour(ZonedDateTime t) {
        ZonedDateTime morningStartDt = ZonedDateTime.of(
                t.getYear(), t.getMonthValue(), t.getDayOfMonth(), MSTART, DEfAULT, DEfAULT, DEfAULT, t.getZone());
        ZonedDateTime morningEndDt = ZonedDateTime.of(
                t.getYear(), t.getMonthValue(), t.getDayOfMonth(), MEND, DEfAULT, DEfAULT, DEfAULT, t.getZone());
        ZonedDateTime afterStartDt = ZonedDateTime.of(
                t.getYear(), t.getMonthValue(), t.getDayOfMonth(), ASTART, DEfAULT, DEfAULT, DEfAULT, t.getZone());
        ZonedDateTime afterEndDt = ZonedDateTime.of(
                t.getYear(), t.getMonthValue(), t.getDayOfMonth(), AEND, DEfAULT, DEfAULT, DEfAULT, t.getZone());

        return (t.isAfter(morningStartDt) || t.isEqual(morningStartDt)) && t.isBefore(morningEndDt) ||
                (t.isAfter(afterStartDt) || t.isEqual(afterStartDt)) && t.isBefore(afterEndDt);
    }

    private static boolean isWorkDate(ZonedDateTime currenDt) {
        if (specialDayMap.containsKey(currenDt.toLocalDate())) {
            return specialDayMap.get(currenDt.toLocalDate()).type.equals(WORKDAY);
        } else {
            return currenDt.toLocalDate().getDayOfWeek().getValue() < 6;
        }
    }

    static void parseICS() {
        try {

            System.setProperty("net.fortuna.ical4j.timezone.cache.impl", MapTimeZoneCache.class.getName());

            InputStream inputStream = ICSParser.class.getClassLoader().getResourceAsStream("CN_zh.ics");
            assert inputStream != null;
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder fileContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("DTSTAMP;VALUE=DATE:")) {
                    String date = line.substring("DTSTAMP;VALUE=DATE:".length());
                    line = "DTSTAMP:" + date + "T000000Z";
                }
                fileContent.append(line).append("\n");
            }
            reader.close();

            CalendarBuilder builder = new CalendarBuilder();
            Calendar calendar = builder.build(new StringReader(fileContent.toString()));

            List<CalendarComponent> components = calendar.getComponents();
            var list = components.stream()
                    .filter(calendarComponent -> calendarComponent.getProperty("DTSTART").isPresent())
                    .filter(calendarComponent -> calendarComponent.getProperty("X-APPLE-SPECIAL-DAY").isPresent())
                    .toList();
            for (CalendarComponent l : list) {

                int countDay = getCountDay(
                        parseTimeZone(l.getProperty("DTSTART").orElseThrow().getValue()),
                        parseTimeZone(l.getProperty("DTEND")
                                .orElse(l.getProperty("DTSTART").orElseThrow()).getValue()));

                LocalDate dtstart = parseLocalDate(l.getProperty("DTSTART").orElseThrow().getValue());

                for (int i = 0; i < countDay; i++) {
                    specialDayMap.put(
                            dtstart.plusDays(i),
                            new SpecialDay(
                                    l.getProperty("SUMMARY").orElseThrow().getValue(),
                                    l.getProperty("X-APPLE-SPECIAL-DAY").orElseThrow().getValue()
                            ));
                }
            }
        } catch (IOException | ParserException e) {
            log.atError().log(e.getMessage());
        }
    }

    private static ZonedDateTime parseTimeZone(String dtstart) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        LocalDate localDate = LocalDate.parse(dtstart, dateFormatter);
        LocalDateTime localDateTime = localDate.atStartOfDay();
        return localDateTime.atZone(ZoneId.of("UTC"));
    }

    private static LocalDate parseLocalDate(String dtstart) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        return LocalDate.parse(dtstart, dateFormatter);
    }

    private static int getCountDay(ZonedDateTime start, ZonedDateTime end) {
        int count = end.getDayOfYear() - start.getDayOfYear();
        return count == 0
                ? 1
                : (count < 0 ? count + 365 : count);
    }
}
