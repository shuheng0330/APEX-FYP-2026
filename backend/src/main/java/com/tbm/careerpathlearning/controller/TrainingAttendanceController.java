package com.tbm.careerpathlearning.controller;

import com.tbm.careerpathlearning.dto.CheckInResponseDto;
import com.tbm.careerpathlearning.dto.StaffDto;
import com.tbm.careerpathlearning.dto.TrainingAttendanceDto;
import com.tbm.careerpathlearning.service.TrainingAttendanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/trainings/attendance")
@CrossOrigin
public class TrainingAttendanceController {

    @Autowired
    private TrainingAttendanceService trainingAttendanceService;

    @PostMapping("/checkin")
    public ResponseEntity<CheckInResponseDto> checkIn(@RequestBody TrainingAttendanceDto trainingAttendanceDto) {
        CheckInResponseDto checkInResponseDto = trainingAttendanceService.checkIn(trainingAttendanceDto);
        return ResponseEntity.ok(checkInResponseDto);
    }

    @GetMapping("/{trainingId}")
    public List<TrainingAttendanceDto> getTrainingAttendanceByTrainingId(@PathVariable Long trainingId) {
        return trainingAttendanceService.findTrainingAttendanceByTrainingId(trainingId);
    }

    @GetMapping("/staff/{trainingId}")
    public List<StaffDto> getStaffAttendanceListByTrainingId(@PathVariable Long trainingId) {
        return trainingAttendanceService.getStaffAttendanceListByTrainingId(trainingId);
    }

    @GetMapping("/by-staff/{staffId}")
    public List<Long> getAttendedTrainingByStaffId(@PathVariable UUID staffId) {
        return trainingAttendanceService.getAttendedTrainingByStaffId(staffId);
    }

}
