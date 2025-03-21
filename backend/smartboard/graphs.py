import os
import matplotlib.pyplot as plt
import matplotlib
import numpy as np
matplotlib.use('Agg')  # GUI를 사용하지 않는 Agg 백엔드 설정
from models import CopPattern

plt.rcParams['axes.unicode_minus'] = False

def generate_cop_distribution_graph(train_id):
    # train_id에 해당하는 모든 CopPattern 레코드 조회
    cop_patterns = CopPattern.query.filter_by(train_id=train_id).all()

    if not cop_patterns:
        raise ValueError(f"No COP data found for Train ID: {train_id}")

    x_cop = [pattern.x_cop for pattern in cop_patterns]
    y_cop = [pattern.y_cop for pattern in cop_patterns]

    # 그래프 저장 경로 설정
    graph_dir = "static/cop_patterns"
    os.makedirs(graph_dir, exist_ok=True)
    graph_path = os.path.join(graph_dir, f"train_{train_id}_cop_distribution.png")

    # 산점도 생성
    plt.figure(figsize=(13, 10))
    plt.scatter(x_cop, y_cop, alpha=0.5, c='#F95454', s=2000, marker='o')  # 점 스타일
    plt.axhline(0, color='gray', linestyle='--', linewidth=0.8)
    plt.axvline(0, color='gray', linestyle='--', linewidth=0.8)
    plt.xlabel('X-axis (Left-Right)', fontsize=26)
    plt.ylabel('Y-axis (Front-Back)', fontsize=26)
    plt.title(f'C.O.P Distribution - Train ID: {train_id}', fontsize=28)
    plt.tight_layout()
    plt.savefig(graph_path)
    plt.close()

    return graph_path

def generate_horizontal_balance_ratio_graph(train_id):
    # train_id에 해당하는 모든 CopPattern 레코드 조회
    cop_patterns = CopPattern.query.filter_by(train_id=train_id).all()

    if not cop_patterns:
        raise ValueError(f"No COP data found for Train ID: {train_id}")

    # x_cop 값 분류
    x_cop_values = [pattern.x_cop for pattern in cop_patterns]
    positive_count = sum(1 for x in x_cop_values if x > 0)
    negative_count = sum(1 for x in x_cop_values if x < 0)
    total_count = len(x_cop_values)

    # 양수와 음수 비율 계산
    positive_ratio = (positive_count / total_count) * 100
    negative_ratio = (negative_count / total_count) * 100
    
    # 그래프 저장 경로 설정
    graph_dir = "static/balance_ratios"
    os.makedirs(graph_dir, exist_ok=True)
    graph_path = os.path.join(graph_dir, f"train_{train_id}_horizontal_balance_ratio.png")

    # 파이차트 데이터
    labels = ['left', 'right']
    sizes = [negative_ratio, positive_ratio]
    colors = ['#ff6f61', '#4da8da']  # 원하는 색상

    # 파이 차트 생성
    plt.figure(figsize=(10, 10))
    plt.pie(sizes, labels=labels, autopct='%1.1f%%',
            colors=colors, startangle=90, textprops={'fontsize': 26})
    plt.axis('equal')  # 원형 차트를 유지
    plt.title(f'Horizontal Balance Ratio', fontsize=28)
    plt.savefig(graph_path)
    plt.close()

    return graph_path
