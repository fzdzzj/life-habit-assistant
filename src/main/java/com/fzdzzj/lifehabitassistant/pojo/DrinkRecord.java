package com.fzdzzj.lifehabitassistant.pojo;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "drink_records")
public class DrinkRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "habit_record_id", nullable = false)
    private HabitRecord habitRecord;
    @Enumerated(EnumType.STRING)
    @Column(name = "drink_type", nullable = false, length = 30)
    private DrinkType drinkType;
    @Column(name = "other_name", length = 50)
    private String otherName;
    @Column(name = "volume_ml", nullable = false)
    private int volumeMl;
    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;
    @Column(length = 500)
    private String note;

    protected DrinkRecord() {
    }

    public DrinkRecord(HabitRecord habitRecord, DrinkType drinkType, String otherName, int volumeMl,
                       LocalDateTime recordedAt, String note) {
        this.habitRecord = habitRecord;
        update(drinkType, otherName, volumeMl, recordedAt, note);
    }

    public void update(DrinkType drinkType, String otherName, int volumeMl, LocalDateTime recordedAt, String note) {
        this.drinkType = drinkType;
        this.otherName = otherName;
        this.volumeMl = volumeMl;
        this.recordedAt = recordedAt;
        this.note = note;
    }

    public Long getId() { return id; }
    public DrinkType getDrinkType() { return drinkType; }
    public String getOtherName() { return otherName; }
    public int getVolumeMl() { return volumeMl; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public String getNote() { return note; }
}
