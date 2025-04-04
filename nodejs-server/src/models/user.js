module.exports = (sequelize, DataTypes) => {
  const User = sequelize.define('User', {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true
    },
    username: {
      type: DataTypes.STRING,
      allowNull: false,
      unique: true,
      validate: {
        len: [3, 30]
      }
    },
    email: {
      type: DataTypes.STRING,
      allowNull: false,
      unique: true,
      validate: {
        isEmail: true
      }
    },
    passwordHash: {
      type: DataTypes.STRING,
      allowNull: false
    },
    displayName: {
      type: DataTypes.STRING,
      allowNull: true
    },
    isActive: {
      type: DataTypes.BOOLEAN,
      defaultValue: true
    },
    lastActiveAt: {
      type: DataTypes.DATE,
      defaultValue: DataTypes.NOW
    }
  }, {
    tableName: 'users',
    timestamps: true
  });

  User.associate = (models) => {
    // Messages sent by user
    User.hasMany(models.Message, {
      foreignKey: 'senderId',
      as: 'sentMessages'
    });

    // Messages received by user
    User.hasMany(models.Message, {
      foreignKey: 'recipientId',
      as: 'receivedMessages'
    });

    // User's refresh tokens
    User.hasMany(models.RefreshToken, {
      foreignKey: 'userId',
      as: 'refreshTokens'
    });
    
    // User's crypto keys
    User.hasOne(models.UserKey, {
      foreignKey: 'userId',
      as: 'userKey'
    });
    
    // User's one-time prekeys
    User.hasMany(models.OneTimePreKey, {
      foreignKey: 'userId',
      as: 'oneTimePrekeys'
    });
  };

  // Create a profile object without sensitive data
  User.prototype.toProfile = function() {
    return {
      id: this.id,
      username: this.username,
      displayName: this.displayName,
      isActive: this.isActive,
      lastActiveAt: this.lastActiveAt
    };
  };

  return User;
}; 