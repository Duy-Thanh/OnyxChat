module.exports = (sequelize, DataTypes) => {
  const UserKey = sequelize.define('UserKey', {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true
    },
    userId: {
      type: DataTypes.UUID,
      allowNull: false,
      unique: true,
      references: {
        model: 'users',
        key: 'id'
      }
    },
    identityKey: {
      type: DataTypes.TEXT,
      allowNull: false
    },
    signedPrekey: {
      type: DataTypes.TEXT,
      allowNull: false
    },
    signedPrekeySignature: {
      type: DataTypes.TEXT,
      allowNull: false
    },
    signedPrekeyId: {
      type: DataTypes.INTEGER,
      allowNull: false
    }
  }, {
    tableName: 'user_keys',
    timestamps: true
  });

  UserKey.associate = (models) => {
    UserKey.belongsTo(models.User, {
      foreignKey: 'userId',
      as: 'user'
    });
  };

  return UserKey;
};