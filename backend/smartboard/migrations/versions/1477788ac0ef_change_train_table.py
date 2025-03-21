"""Change Train table

Revision ID: 1477788ac0ef
Revises: deb95d4224e9
Create Date: 2024-11-26 11:46:25.755423

"""
from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = '1477788ac0ef'
down_revision = 'deb95d4224e9'
branch_labels = None
depends_on = None


def upgrade():
    # ### commands auto generated by Alembic - please adjust! ###
    with op.batch_alter_table('train', schema=None) as batch_op:
        batch_op.alter_column('total_balance_sustain_time',
               existing_type=sa.INTEGER(),
               nullable=True)

    # ### end Alembic commands ###


def downgrade():
    # ### commands auto generated by Alembic - please adjust! ###
    with op.batch_alter_table('train', schema=None) as batch_op:
        batch_op.alter_column('total_balance_sustain_time',
               existing_type=sa.INTEGER(),
               nullable=False)

    # ### end Alembic commands ###
