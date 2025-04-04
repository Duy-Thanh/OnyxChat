module.exports = (sequelize, DataTypes) => {
  const OneTimePreKey = sequelize.define('OneTimePreKey', {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true
    },
    userId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: {
        model: 'users',
        key: 'id'
      }
    },
    prekeyId: {
      type: DataTypes.INTEGER,
      allowNull: false
    },
    prekey: {
      type: DataTypes.TEXT,
      allowNull: false
    },
    used: {
      type: DataTypes.BOOLEAN,
      defaultValue: false
    },
    usedAt: {
      type: DataTypes.DATE,
      allowNull: true
    }
  }, {
    tableName: 'one_time_prekeys',
    timestamps: true,
    indexes: [
      {
        unique: true,
        fields: ['userId', 'prekeyId']
      }
    ]
  });

  OneTimePreKey.associate = (models) => {
    OneTimePreKey.belongsTo(models.User, {
      foreignKey: 'userId',
      as: 'user'
    });
  };

  return OneTimePreKey;
}; 