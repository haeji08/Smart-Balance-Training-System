from datetime import date, time

def insert_dummy_data(db, Train):
    """
    더미 데이터를 Train 테이블에 삽입합니다.
    :param db: SQLAlchemy 데이터베이스 객체
    :param Train: Train 모델 클래스
    """
    # Train 테이블에 데이터가 없으면 삽입
    if not Train.query.first():
        today = date.today()  # 오늘 날짜 가져오기
        dummy_data = [
            Train(
                date=today,  # 오늘 날짜
                start_time=time(9, 0),
                end_time=time(10, 0),
                total_balance_sustain_time=30,
                cop_pattern="pattern_1.png",
                total_unbalance_count=5,
                horizontal_balance_ratio="80:20",
                self_feedback="Good balance!"
            ),
            Train(
                date=today,  # 오늘 날짜
                start_time=time(10, 0),
                end_time=time(11, 0),
                total_balance_sustain_time=20,
                cop_pattern="pattern_2.png",
                total_unbalance_count=8,
                horizontal_balance_ratio="70:30",
                self_feedback="Keep improving."
            ),
            Train(
                date=today,  # 오늘 날짜
                start_time=time(11, 0),
                end_time=time(12, 0),
                total_balance_sustain_time=15,
                cop_pattern="pattern_3.png",
                total_unbalance_count=3,
                horizontal_balance_ratio="90:10",
                self_feedback="Excellent session!"
            ),
            Train(
                date=today,  # 오늘 날짜
                start_time=time(12, 0),
                end_time=time(13, 0),
                total_balance_sustain_time=10,
                cop_pattern="pattern_4.png",
                total_unbalance_count=10,
                horizontal_balance_ratio="50:50",
                self_feedback="Needs work on balance."
            ),
            Train(
                date=today,  # 오늘 날짜
                start_time=time(13, 0),
                end_time=time(14, 0),
                total_balance_sustain_time=25,
                cop_pattern="pattern_5.png",
                total_unbalance_count=2,
                horizontal_balance_ratio="85:15",
                self_feedback="Great improvement!"
            ),
        ]
        db.session.bulk_save_objects(dummy_data)  # 더미 데이터 한 번에 저장
        db.session.commit()
        print("Dummy data inserted successfully!")
    else:
        print("Data already exists in the Train table.")
