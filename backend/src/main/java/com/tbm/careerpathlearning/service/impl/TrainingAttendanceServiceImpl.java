package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.CheckInResponseDto;
import com.tbm.careerpathlearning.dto.StaffDto;
import com.tbm.careerpathlearning.dto.TrainingAttendanceDto;
import com.tbm.careerpathlearning.mapper.AppMapper;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.TrainingAttendance;
import com.tbm.careerpathlearning.model.TrainingProgram;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.repository.TrainingAttendanceRepository;
import com.tbm.careerpathlearning.repository.TrainingProgramRepository;
import com.tbm.careerpathlearning.service.TrainingAttendanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TrainingAttendanceServiceImpl implements TrainingAttendanceService {

    private static final double EARTH_RADIUS = 6371000;
    private static final double RADIUS = 100;

    @Autowired
    private TrainingAttendanceRepository trainingAttendanceRepository;

    @Autowired
    private TrainingProgramRepository trainingProgramRepository;

    @Autowired
    private StaffRepository staffRepository;
    @Autowired
    private AppMapper appMapper;

    @Override
    public CheckInResponseDto checkIn(TrainingAttendanceDto dto) {
        TrainingProgram training = trainingProgramRepository.findById(dto.getTrainingId())
                .orElseThrow(() -> new RuntimeException("Training not found"));

        double distance = calculateDistance(training.getLatitude(), training.getLongitude(),
                dto.getCheckInLatitude(), dto.getCheckInLongitude());

        boolean inside = distance <= RADIUS;
        dto.setCheckInTime(LocalDateTime.now());
        dto.setWithinGeofence(inside);

        if (dto.getWithinGeofence()){
            TrainingAttendance attendance = mapToEntity(dto);
            trainingAttendanceRepository.save(attendance);
        }

        String message = inside
                ? "Check-in successful!"
                : "You are outside the training location (" + String.format("%.1f", distance) + "m away).";

        return new CheckInResponseDto(inside, message, distance);
    }

    @Override
    public List<StaffDto> getStaffAttendanceListByTrainingId(Long trainingId) {
        return trainingAttendanceRepository.findStaffAttendanceByTrainingProgramId(trainingId)
                .stream().map(appMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<TrainingAttendanceDto> findTrainingAttendanceByTrainingId(Long trainingId) {
        return trainingAttendanceRepository.findTrainingAttendanceByTrainingProgram_TrainingIdAndTrainingProgram_IsDeletedFalse(trainingId)
                .stream().map(appMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<Long> getAttendedTrainingByStaffId(UUID staffId){
        return trainingAttendanceRepository.findTrainingAttendanceIdByStaffId(staffId);
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }


    private TrainingAttendance mapToEntity(TrainingAttendanceDto trainingAttendanceDto) {
        TrainingAttendance trainingAttendance = new TrainingAttendance();
        Staff staff = staffRepository.findById(trainingAttendanceDto.getStaffId())
                .orElseThrow(() -> new RuntimeException("Staff not found"));

        TrainingProgram training = trainingProgramRepository.findById(trainingAttendanceDto.getTrainingId())
                        .orElseThrow(() -> new RuntimeException("Training not found"));

        trainingAttendance.setStaff(staff);
        trainingAttendance.setTrainingProgram(training);
        trainingAttendance.setCheckInLatitude(trainingAttendanceDto.getCheckInLatitude());
        trainingAttendance.setCheckInLongitude(trainingAttendanceDto.getCheckInLongitude());
        trainingAttendance.setWithinGeofence(trainingAttendanceDto.getWithinGeofence());
        return trainingAttendance;
    }
}
