module.exports = (sequelize, DataTypes) => {
  const Session = sequelize.define('Session', {
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
    otherUserId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: {
        model: 'users',
        key: 'id'
      }
    },
    sessionData: {
      type: DataTypes.TEXT,
      allowNull: false
    }
  }, {
    tableName: 'sessions',
    timestamps: true,
    indexes: [
      {
        unique: true,
        fields: ['userId', 'otherUserId']
      }
    ]
  });

  Session.associate = (models) => {
    Session.belongsTo(models.User, {
      foreignKey: 'userId',
      as: 'user'
    });

    Session.belongsTo(models.User, {
      foreignKey: 'otherUserId',
      as: 'otherUser'
    });
  };

  return Session;
}; 