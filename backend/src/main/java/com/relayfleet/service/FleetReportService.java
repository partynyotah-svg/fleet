package com.relayfleet.service;

import com.relayfleet.domain.FleetPayment;
import com.relayfleet.domain.FleetPaymentRepository;
import com.relayfleet.domain.GeofenceEvent;
import com.relayfleet.domain.GeofenceEventRepository;
import com.relayfleet.domain.Trip;
import com.relayfleet.domain.TripRepository;
import com.relayfleet.domain.Vehicle;
import com.relayfleet.domain.VehicleRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Service;

@Service
public class FleetReportService {
    private final VehicleRepository vehicles;
    private final TripRepository trips;
    private final FleetPaymentRepository payments;
    private final GeofenceEventRepository geofenceEvents;

    public FleetReportService(VehicleRepository vehicles, TripRepository trips, FleetPaymentRepository payments,
            GeofenceEventRepository geofenceEvents) {
        this.vehicles = vehicles;
        this.trips = trips;
        this.payments = payments;
        this.geofenceEvents = geofenceEvents;
    }

    public byte[] pdf() {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            List<String> lines = reportLines();
            int offset = 0;
            while (offset < lines.size()) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
                    stream.newLineAtOffset(48, 744);
                    stream.showText("Relay Fleet Operations Report");
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
                    stream.newLineAtOffset(0, -24);
                    int count = 0;
                    while (offset < lines.size() && count < 48) {
                        stream.showText(ascii(lines.get(offset++)));
                        stream.newLineAtOffset(0, -13);
                        count++;
                    }
                    stream.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("Could not generate PDF report", error);
        }
    }

    public byte[] excel() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet fleet = workbook.createSheet("Fleet");
            String[] headers = {"Vehicle code", "Make", "Model", "Year", "Driver", "Status", "Fuel %", "Odometer km", "Latitude", "Longitude", "Last update"};
            writeHeader(fleet, headers);
            int rowIndex = 1;
            for (Vehicle vehicle : vehicles.findAllByOrderByVehicleCodeAsc()) {
                Row row = fleet.createRow(rowIndex++);
                Object[] values = {vehicle.getVehicleCode(), vehicle.getMake(), vehicle.getModel(), vehicle.getModelYear(), vehicle.getDriverName(), vehicle.getStatus(), vehicle.getFuelPercent(), vehicle.getOdometerKm(), vehicle.getLatitude(), vehicle.getLongitude(), vehicle.getLastSeenAt()};
                writeValues(row, values);
            }
            Sheet ledger = workbook.createSheet("Payments");
            writeHeader(ledger, new String[]{"Paid at", "Payer", "Amount", "Currency", "Category", "Method", "Reference", "Recorded by"});
            rowIndex = 1;
            for (FleetPayment payment : payments.findTop500ByOrderByPaidAtDesc()) {
                Row row = ledger.createRow(rowIndex++);
                writeValues(row, new Object[]{payment.getPaidAt(), payment.getPayerName(), payment.getAmount(), payment.getCurrency(), payment.getCategory(), payment.getPaymentMethod(), payment.getReference(), payment.getRecordedBy()});
            }
            Sheet boundaries = workbook.createSheet("Geofence events");
            writeHeader(boundaries, new String[]{"Captured at", "Vehicle", "Zone", "Event"});
            rowIndex = 1;
            for (GeofenceEvent event : geofenceEvents.findTop100ByOrderByCapturedAtDesc()) {
                Row row = boundaries.createRow(rowIndex++);
                writeValues(row, new Object[]{event.getCapturedAt(), event.getVehicleCode(), event.getGeofenceName(), event.getType()});
            }
            for (Sheet sheet : workbook) {
                sheet.createFreezePane(0, 1);
                for (int column = 0; column < sheet.getRow(0).getLastCellNum(); column++) sheet.autoSizeColumn(column);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("Could not generate Excel report", error);
        }
    }

    public byte[] word() {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Relay Fleet Operations Report");
            document.createParagraph().createRun().setText("Generated " + Instant.now());
            document.createParagraph().createRun().setText("Fleet register");
            String[] headers = {"Vehicle", "Make / model", "Driver", "Status", "Fuel", "Last update"};
            XWPFTable fleet = document.createTable(1, headers.length);
            for (int index = 0; index < headers.length; index++) fleet.getRow(0).getCell(index).setText(headers[index]);
            for (Vehicle vehicle : vehicles.findAllByOrderByVehicleCodeAsc()) {
                var cells = fleet.createRow().getTableCells();
                String[] values = {vehicle.getVehicleCode(), vehicle.getMake() + " " + vehicle.getModel(), vehicle.getDriverName(), vehicle.getStatus().name(), vehicle.getFuelPercent() == null ? "-" : vehicle.getFuelPercent() + "%", String.valueOf(vehicle.getLastSeenAt() == null ? "No reading" : vehicle.getLastSeenAt())};
                for (int index = 0; index < values.length; index++) cells.get(index).setText(values[index]);
            }
            document.createParagraph().createRun().setText("Recent trips");
            String[] tripHeaders = {"Trip", "Vehicle", "Destination", "Planned", "Status"};
            XWPFTable tripTable = document.createTable(1, tripHeaders.length);
            for (int index = 0; index < tripHeaders.length; index++) tripTable.getRow(0).getCell(index).setText(tripHeaders[index]);
            for (Trip trip : trips.findTop200ByOrderByPlannedStartDesc()) {
                var cells = tripTable.createRow().getTableCells();
                String[] values = {trip.getTripCode(), trip.getVehicle().getVehicleCode(), trip.getDestination(), String.valueOf(trip.getPlannedStart()), trip.getStatus().name()};
                for (int index = 0; index < values.length; index++) cells.get(index).setText(values[index]);
            }
            document.createParagraph().createRun().setText("Payment ledger");
            String[] paymentHeaders = {"Paid at", "Payer", "Amount", "Currency", "Category", "Method"};
            XWPFTable paymentTable = document.createTable(1, paymentHeaders.length);
            for (int index = 0; index < paymentHeaders.length; index++) paymentTable.getRow(0).getCell(index).setText(paymentHeaders[index]);
            for (FleetPayment payment : payments.findTop500ByOrderByPaidAtDesc()) {
                var cells = paymentTable.createRow().getTableCells();
                String[] values = {String.valueOf(payment.getPaidAt()), payment.getPayerName(), payment.getAmount().toPlainString(), payment.getCurrency(), payment.getCategory(), payment.getPaymentMethod()};
                for (int index = 0; index < values.length; index++) cells.get(index).setText(values[index]);
            }
            document.createParagraph().createRun().setText("Recent geofence events");
            String[] eventHeaders = {"Captured at", "Vehicle", "Zone", "Event"};
            XWPFTable eventTable = document.createTable(1, eventHeaders.length);
            for (int index = 0; index < eventHeaders.length; index++) eventTable.getRow(0).getCell(index).setText(eventHeaders[index]);
            for (GeofenceEvent event : geofenceEvents.findTop100ByOrderByCapturedAtDesc()) {
                var cells = eventTable.createRow().getTableCells();
                String[] values = {String.valueOf(event.getCapturedAt()), event.getVehicleCode(), event.getGeofenceName(), event.getType().name()};
                for (int index = 0; index < values.length; index++) cells.get(index).setText(values[index]);
            }
            document.write(output);
            return output.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("Could not generate Word report", error);
        }
    }

    private List<String> reportLines() {
        var fleet = vehicles.findAllByOrderByVehicleCodeAsc();
        var recentTrips = trips.findTop200ByOrderByPlannedStartDesc();
        var recentPayments = payments.findTop500ByOrderByPaidAtDesc();
        var recentBoundaryEvents = geofenceEvents.findTop100ByOrderByCapturedAtDesc();
        var lines = new java.util.ArrayList<String>();
        lines.add("Generated: " + Instant.now());
        lines.add("Vehicles: " + fleet.size() + " | Trips: " + recentTrips.size());
        lines.add("");
        lines.add("FLEET REGISTER");
        lines.add("Vehicle | Make / model | Driver | Status | Fuel | Last update");
        for (Vehicle vehicle : fleet) lines.add(vehicle.getVehicleCode() + " | " + vehicle.getMake() + " " + vehicle.getModel() + " | " + vehicle.getDriverName() + " | " + vehicle.getStatus() + " | " + (vehicle.getFuelPercent() == null ? "-" : vehicle.getFuelPercent() + "%") + " | " + (vehicle.getLastSeenAt() == null ? "No reading" : vehicle.getLastSeenAt()));
        lines.add("");
        lines.add("RECENT TRIPS");
        for (Trip trip : recentTrips) lines.add(trip.getTripCode() + " | " + trip.getVehicle().getVehicleCode() + " | " + trip.getDestination() + " | " + trip.getStatus());
        lines.add("");
        lines.add("PAYMENT LEDGER");
        for (FleetPayment payment : recentPayments) lines.add(payment.getPaidAt() + " | " + payment.getPayerName() + " | " + payment.getCurrency() + " " + payment.getAmount() + " | " + payment.getCategory() + " | " + payment.getPaymentMethod());
        lines.add("");
        lines.add("RECENT GEOFENCE EVENTS");
        for (GeofenceEvent event : recentBoundaryEvents) lines.add(event.getCapturedAt() + " | " + event.getVehicleCode() + " | " + event.getGeofenceName() + " | " + event.getType());
        return lines;
    }

    private static void writeHeader(Sheet sheet, String[] headers) {
        Row row = sheet.createRow(0);
        for (int index = 0; index < headers.length; index++) row.createCell(index).setCellValue(headers[index]);
    }

    private static void writeValues(Row row, Object[] values) {
        for (int index = 0; index < values.length; index++) {
            Object value = values[index];
            if (value instanceof Number number) row.createCell(index).setCellValue(number.doubleValue());
            else row.createCell(index).setCellValue(value == null ? "" : value.toString());
        }
    }

    private static String ascii(String value) {
        return value.replaceAll("[^\\x20-\\x7E]", "?");
    }
}